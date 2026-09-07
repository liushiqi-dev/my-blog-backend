package com.liushiqi.blogmain.config;

import com.liushiqi.blogmain.common.util.TextChunker;
import com.liushiqi.blogmain.entity.KbChunk;
import com.liushiqi.blogmain.kb.VectorSearcher;
import com.liushiqi.blogmain.mapper.KbChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化 RAG 向量检索层：幂等建索引，索引为空且 kb_chunk 有数据时从持久化的
 * 向量字节零成本重建（embedding 字节即事实源，全程不调用 LLM/embedding 接口）。
 * <p>
 * 任何异常只记日志不抛出——向量层故障不应阻断博客主站启动。
 */
@Component
@Slf4j
public class KbBootstrapRunner implements ApplicationRunner {

    private final VectorSearcher vectorSearcher;
    private final KbChunkMapper kbChunkMapper;
    private final RagProperties props;

    public KbBootstrapRunner(VectorSearcher vectorSearcher, KbChunkMapper kbChunkMapper, RagProperties props) {
        this.vectorSearcher = vectorSearcher;
        this.kbChunkMapper = kbChunkMapper;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            vectorSearcher.ensureIndex();
            if (vectorSearcher.countDocs() > 0) {
                return;
            }
            if (kbChunkMapper.countAll() == 0) {
                return;
            }
            rebuild();
        } catch (Exception e) {
            log.error("向量索引初始化/重建失败，跳过（不影响主站功能）", e);
        }
    }

    /**
     * 从 kb_chunk 的 embedding 字节逐条回灌索引；跳过无向量与维度不符的行，
     * 维度不符通常意味着 rag.embedding-dimension 在向量生成后被调整过，照抄会污染 HNSW 索引。
     */
    private void rebuild() {
        int expectedBytes = props.getEmbeddingDimension() * Float.BYTES;
        int rebuilt = 0;
        int skipped = 0;
        for (KbChunk chunk : kbChunkMapper.selectAllForRebuild()) {
            byte[] embedding = chunk.getEmbedding();
            if (embedding == null || embedding.length != expectedBytes) {
                skipped++;
                continue;
            }
            vectorSearcher.upsert(chunk.getId(), TextChunker.toFloatArray(embedding), chunk.getPostId());
            rebuilt++;
        }
        log.info("向量索引重建完成：写入 {} 条，跳过 {} 条（无向量或维度不符）", rebuilt, skipped);
    }
}
