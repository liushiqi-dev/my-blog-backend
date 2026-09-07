package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.util.TextChunker;
import com.liushiqi.blogmain.config.RagProperties;
import com.liushiqi.blogmain.entity.KbChunk;
import com.liushiqi.blogmain.kb.Hit;
import com.liushiqi.blogmain.kb.LlmClient;
import com.liushiqi.blogmain.kb.VectorSearcher;
import com.liushiqi.blogmain.mapper.KbChunkMapper;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.service.KbService;
import com.liushiqi.blogmain.vo.AskVo;
import com.liushiqi.blogmain.vo.PageVo;
import com.liushiqi.blogmain.vo.PostVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAG 知识库业务实现：索引重建（reindex）与检索增强问答（ask）。
 * <p>
 * 事实源分工：分块原文、SHA-256 指纹与向量字节持久化在 kb_chunk 表，Redis 只承载易失的 HNSW 检索结构，
 * 两者无法原子写入，因此所有写入路径都设计为幂等，靠重复 reindex 或启动重建收敛到一致。
 */
@Slf4j
@Service
public class KbServiceImpl implements KbService {

    /** 只索引已发布文章，草稿不进入知识库。 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** reindex 翻页步长：每批只取文章 id 列表，正文逐篇按需载入，避免整表全文驻留内存。 */
    private static final int REINDEX_PAGE_SIZE = 50;

    /** 拼接 Prompt 时单个资料块的最大字符数，与 top-k 共同约束上下文规模。 */
    private static final int CONTEXT_BLOCK_LIMIT = 800;

    /** 资料不足时的降级话术，与 system 提示词中的兜底回答保持一致。 */
    private static final String FALLBACK_ANSWER = "根据现有博客内容无法回答";

    private static final String SYSTEM_PROMPT =
            "你是博客问答助手。只依据下面提供的资料回答用户问题；资料中没有的信息，回答\"根据现有博客内容无法回答\"。不要编造。";

    private final PostMapper postMapper;
    private final KbChunkMapper kbChunkMapper;
    private final LlmClient llmClient;
    private final VectorSearcher vectorSearcher;
    private final RagProperties props;

    public KbServiceImpl(PostMapper postMapper,
                         KbChunkMapper kbChunkMapper,
                         LlmClient llmClient,
                         VectorSearcher vectorSearcher,
                         RagProperties props) {
        this.postMapper = postMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.llmClient = llmClient;
        this.vectorSearcher = vectorSearcher;
        this.props = props;
    }

    /**
     * 重建知识库索引。
     * <p>
     * 不加 {@code @Transactional}：一次重建包含数十次 embedding 网络调用，事务会长时间占用数据库连接；
     * kb_chunk 的 upsert 依赖唯一键 uk_post_chunk 天然幂等，单篇失败只影响该篇，
     * 残留的部分索引由下次 reindex 或启动时的 KbBootstrapRunner 补齐。
     *
     * @return 本次索引覆盖的分块总数（含复用未变分块，重复执行结果一致）
     */
    @Override
    public int reindex() {
        // Redis 可能被重启或清空，重建前幂等确保索引存在
        vectorSearcher.ensureIndex();
        Integer total = postMapper.getTotal(STATUS_PUBLISHED);
        int postCount = total == null ? 0 : total;
        Stats stats = new Stats();
        int failed = 0;
        // findPage 的首个参数在 SQL 中是 limit 的偏移量（PostServiceImpl.listPosts 传的是 (page-1)*size），
        // 故这里直接以 0、50、100... 递增翻页，与既有分页语义一致
        for (int offset = 0; offset < postCount; offset += REINDEX_PAGE_SIZE) {
            List<PageVo> page = postMapper.findPage(offset, REINDEX_PAGE_SIZE, STATUS_PUBLISHED);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (PageVo item : page) {
                long postId = item.getId().longValue();
                try {
                    PostVo post = postMapper.findById(postId);
                    if (post == null) {
                        // findById 内联分类表，文章无分类关联或已删除时查不到，无正文可索引
                        log.warn("文章 {} 详情查询为空，跳过索引", postId);
                        continue;
                    }
                    indexPost(post, stats);
                } catch (Exception e) {
                    failed++;
                    log.error("文章 {} 索引失败，跳过", postId, e);
                }
            }
        }
        log.info("索引重建完成：已发布文章 {} 篇，分块 {} 个（新向量化 {}、复用 {}、写库 {}），失败 {} 篇，Redis 文档数 {}",
                postCount, stats.indexed, stats.embedded, stats.reused, stats.written, failed,
                vectorSearcher.countDocs());
        if (failed > 0 && stats.indexed == 0) {
            throw new BusinessException("索引重建失败：" + failed + " 篇文章处理异常，详见日志");
        }
        return stats.indexed;
    }

    /**
     * 索引单篇文章：分块 → 按 chunk_index 与已有分块比对指纹 → 仅对变化的分块调用 embedding →
     * upsert 到 kb_chunk → 回查自增 id 后写入 Redis 向量。
     * <p>
     * 只对正文分块，不把标题拼进分块文本：标题一旦修改会改变全部分块内容与偏移，
     * 导致整篇指纹失效并重新向量化；标题改由问答阶段作为资料块的来源标注提供给模型。
     * <p>
     * 残留清理策略：分块数变少时尾部 chunk_index 不再被本次 upsert 覆盖，而 KbChunkMapper
     * 只提供整篇 deleteByPostId、没有按 chunk_index 范围删除的方法，因此该场景先整篇清空
     * MySQL 行与 Redis 向量再全量重写；旧向量字节已在内存中留存，内容未变的分块仍复用旧向量，
     * 不产生额外 embedding 调用。
     */
    private void indexPost(PostVo post, Stats stats) {
        long postId = post.getId();
        List<String> texts = TextChunker.chunk(post.getContent(), props.getChunkSize(), props.getChunkOverlap());
        List<KbChunk> existing = kbChunkMapper.selectByPostId(postId);
        if (texts.isEmpty()) {
            // 正文为空或纯空白：无可索引内容，清理该文章的历史分块与向量
            if (!existing.isEmpty()) {
                vectorSearcher.removeByPostId(postId);
                kbChunkMapper.deleteByPostId(postId);
                log.info("文章 {} 正文为空，已清理 {} 个历史分块", postId, existing.size());
            }
            return;
        }

        Map<Integer, KbChunk> oldByIndex = new HashMap<>();
        for (KbChunk chunk : existing) {
            oldByIndex.put(chunk.getChunkIndex(), chunk);
        }
        boolean shrink = existing.size() > texts.size();
        if (shrink) {
            vectorSearcher.removeByPostId(postId);
            kbChunkMapper.deleteByPostId(postId);
        }

        int expectedBytes = props.getEmbeddingDimension() * Float.BYTES;
        List<Integer> toEmbed = new ArrayList<>();
        Set<Integer> toWrite = new LinkedHashSet<>();
        Map<Integer, byte[]> reusedBytes = new HashMap<>();
        Map<Integer, String> hashes = new HashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            String hash = TextChunker.sha256(texts.get(i));
            hashes.put(i, hash);
            KbChunk old = oldByIndex.get(i);
            // 指纹一致且旧向量维度匹配才算可复用；维度不符说明 embedding-dimension 被调整过，必须重算
            boolean reusable = old != null && hash.equals(old.getContentHash())
                    && old.getEmbedding() != null && old.getEmbedding().length == expectedBytes;
            if (reusable) {
                reusedBytes.put(i, old.getEmbedding());
                stats.reused++;
                // 整篇清空后必须重写；行仍存在且内容未变时跳过写库，避免无意义的 update
                if (shrink) {
                    toWrite.add(i);
                }
            } else {
                toEmbed.add(i);
                toWrite.add(i);
            }
        }

        // 批量向量化：LlmClient.embed 内部按 embeddingBatchSize 切片，并保证返回顺序与入参一致
        Map<Integer, float[]> newVectors = new HashMap<>();
        if (!toEmbed.isEmpty()) {
            List<String> pending = new ArrayList<>(toEmbed.size());
            for (Integer index : toEmbed) {
                pending.add(texts.get(index));
            }
            List<float[]> vectors = llmClient.embed(pending);
            for (int j = 0; j < toEmbed.size(); j++) {
                newVectors.put(toEmbed.get(j), vectors.get(j));
            }
            stats.embedded += toEmbed.size();
        }

        LocalDateTime now = LocalDateTime.now();
        for (Integer index : toWrite) {
            byte[] embedding = reusedBytes.containsKey(index)
                    ? reusedBytes.get(index)
                    : TextChunker.toBytes(newVectors.get(index));
            KbChunk old = oldByIndex.get(index);
            KbChunk chunk = new KbChunk();
            chunk.setPostId(postId);
            chunk.setChunkIndex(index);
            chunk.setContent(texts.get(index));
            chunk.setContentHash(hashes.get(index));
            chunk.setEmbedding(embedding);
            // 整篇重写时沿用原创建时间，保持分块的首次入库时间语义
            chunk.setCreateTime(old != null && old.getCreateTime() != null ? old.getCreateTime() : now);
            chunk.setUpdateTime(now);
            kbChunkMapper.upsert(chunk);
            stats.written++;
        }

        // upsert 未配置 useGeneratedKeys，拿不到自增 id；写库完成后回查该文章的分块，
        // 取 (chunk_index -> id) 映射作为 Redis 文档 key kb:chunk:{id} 的来源
        Map<Integer, Long> idByIndex = new HashMap<>();
        for (KbChunk chunk : kbChunkMapper.selectByPostId(postId)) {
            idByIndex.put(chunk.getChunkIndex(), chunk.getId());
        }
        try {
            // 全部分块都重写 Redis：HSET 幂等且零 token 成本，可自愈索引丢失或与 MySQL 漂移的分块
            for (int i = 0; i < texts.size(); i++) {
                Long chunkId = idByIndex.get(i);
                if (chunkId == null) {
                    log.warn("文章 {} 分块 {} 未查回自增 id，跳过向量写入", postId, i);
                    continue;
                }
                float[] vector = newVectors.containsKey(i)
                        ? newVectors.get(i)
                        : TextChunker.toFloatArray(reusedBytes.get(i));
                vectorSearcher.upsert(chunkId, vector, postId);
            }
        } catch (Exception e) {
            // Redis 写失败不回滚 MySQL：kb_chunk 已存下向量字节，重启时 KbBootstrapRunner 会据此重建索引
            log.error("文章 {} 向量写入 Redis 失败，MySQL 分块已保留", postId, e);
        }
        stats.indexed += texts.size();
    }

    /**
     * 知识库问答：问题向量化 → KNN 召回 top-k 分块 → 拼接截断后的资料上下文 → LLM 生成回答。
     * <p>
     * 召回为空时不调用 chat 直接降级，避免模型凭参数记忆编造博客内容；
     * LlmClient 与 VectorSearcher 抛出的 BusinessException 向上传播，由 GlobalExceptionHandler 转统一失败响应。
     */
    @Override
    public AskVo ask(String question) {
        List<float[]> queryVectors = llmClient.embed(List.of(question));
        List<Hit> hits = queryVectors.isEmpty()
                ? List.of()
                : vectorSearcher.topK(queryVectors.get(0), props.getTopK());
        if (hits.isEmpty()) {
            log.info("知识库无召回结果，返回降级答案");
            return new AskVo(FALLBACK_ANSWER, List.of());
        }

        List<Long> chunkIds = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            chunkIds.add(hit.chunkId());
        }
        Map<Long, KbChunk> chunkById = new HashMap<>();
        for (KbChunk chunk : kbChunkMapper.selectContentByIds(chunkIds)) {
            chunkById.put(chunk.getId(), chunk);
        }

        // 按命中顺序对文章去重取标题，既用于资料块的来源标注，也用于组装 sources
        Map<Long, String> titleByPostId = new LinkedHashMap<>();
        for (Hit hit : hits) {
            if (titleByPostId.containsKey(hit.postId())) {
                continue;
            }
            PostVo post = postMapper.findById(hit.postId());
            titleByPostId.put(hit.postId(), post == null ? null : post.getTitle());
        }

        StringBuilder context = new StringBuilder();
        int blockNo = 0;
        // IN 查询结果无序，按 hits 顺序重排，相似度高的资料靠前
        for (Hit hit : hits) {
            KbChunk chunk = chunkById.get(hit.chunkId());
            if (chunk == null) {
                // Redis 向量文档残留而 kb_chunk 行已删除，跳过该块
                log.warn("分块 {} 在 kb_chunk 中不存在，跳过", hit.chunkId());
                continue;
            }
            String title = titleByPostId.get(hit.postId());
            context.append("【资料").append(++blockNo).append("】来源：")
                    .append(title == null ? "文章ID " + hit.postId() : "《" + title + "》")
                    .append('\n').append(truncate(chunk.getContent())).append("\n\n");
        }
        if (blockNo == 0) {
            return new AskVo(FALLBACK_ANSWER, List.of());
        }

        String answer = llmClient.chat(SYSTEM_PROMPT, context.append("问题：").append(question).toString());
        // 模型按 system 提示词判定资料不足时会回降级话术，此时 KNN 召回的分块与问题并不相关
        // （topK 无相似度阈值，总返回 k 条最近邻），继续当作引用来源返回会让用户误以为答案有出处，
        // 故与降级话术一并清空 sources。
        if (isFallbackAnswer(answer)) {
            log.info("模型判定资料不足，返回降级答案且不附带引用来源");
            return new AskVo(FALLBACK_ANSWER, List.of());
        }
        List<AskVo.Source> sources = new ArrayList<>();
        titleByPostId.forEach((postId, title) -> {
            // 标题取不到说明文章已删除或无分类关联，不作为可点击来源返回
            if (title != null) {
                sources.add(new AskVo.Source(postId, title));
            }
        });
        return new AskVo(answer, sources);
    }

    /**
     * 判断模型是否回了降级话术：去掉空白与中英文标点后，以 {@link #FALLBACK_ANSWER} 开头且未显著展开。
     * <p>
     * 模型实际返回常带句号（“根据现有博客内容无法回答。”），故不能直接 equals；长度上限用于排除
     * “无法回答…但关于 X 部分…”这类其实给出了实质内容的回复，避免误清掉有效来源。
     */
    private static boolean isFallbackAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        String normalized = stripPunct(answer);
        String fallback = stripPunct(FALLBACK_ANSWER);
        return normalized.startsWith(fallback) && normalized.length() <= fallback.length() * 2;
    }

    /**
     * 剔除空白与常见中英文标点，用于降级话术的宽松比对。
     */
    private static String stripPunct(String text) {
        return text.replaceAll("[\\s。，、；：！？「」『』（）《》.,;:!?()\\[\\]\"']", "");
    }

    /**
     * 截断资料块文本，控制单次 chat 的上下文 token 规模。
     */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.strip();
        return stripped.length() <= CONTEXT_BLOCK_LIMIT
                ? stripped
                : stripped.substring(0, CONTEXT_BLOCK_LIMIT) + "...";
    }

    /**
     * reindex 过程的计数汇总，用于日志与返回值。
     */
    private static final class Stats {
        /** 索引覆盖的分块总数 */
        private int indexed;
        /** 实际调用 embedding 的分块数 */
        private int embedded;
        /** 指纹命中而复用旧向量的分块数 */
        private int reused;
        /** 写入 kb_chunk 的分块数 */
        private int written;
    }
}
