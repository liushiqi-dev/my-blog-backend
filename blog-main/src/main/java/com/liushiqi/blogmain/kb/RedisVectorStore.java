package com.liushiqi.blogmain.kb;

import com.liushiqi.blogmain.common.util.TextChunker;
import com.liushiqi.blogmain.config.RagProperties;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Redis 8 Query Engine（HNSW + COSINE）之上的向量存取实现。
 * <p>
 * Lettuce 无类型化 FT.* API，所有查询与向量写入均通过 RedisCallback 以原始 byte[] 派发，
 * 完全绕开 RedisTemplate 的 JSON 序列化器——向量字节是 FLOAT32 二进制，经 JSON 序列化会被污染。
 * <p>
 * 数据形态：key {@code kb:chunk:{chunkId}} 的 HASH，字段 embedding 为 FLOAT32 小端字节、
 * post_id 为 TAG；索引 kb_idx 以 {@code PREFIX 1 kb:chunk:} 自动索引这些 HASH，
 * HSET 命中前缀即入索引，无需显式 ADD。
 */
@Component
@Slf4j
public class RedisVectorStore implements VectorSearcher {

    private static final String INDEX_NAME = "kb_idx";
    private static final String KEY_PREFIX = "kb:chunk:";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_POST_ID = "post_id";
    private static final String FIELD_SCORE = "score";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties props;

    public RedisVectorStore(RedisTemplate<String, Object> redisTemplate, RagProperties props) {
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    @Override
    public void ensureIndex() {
        int dim = props.getEmbeddingDimension();
        try {
            dispatch("FT.CREATE",
                    b(INDEX_NAME),
                    b("ON"), b("HASH"),
                    b("PREFIX"), b("1"), b(KEY_PREFIX),
                    b("SCHEMA"),
                    b(FIELD_EMBEDDING), b("VECTOR"), b("HNSW"), b("6"),
                    b("TYPE"), b("FLOAT32"),
                    b("DIM"), b(String.valueOf(dim)),
                    b("DISTANCE_METRIC"), b("COSINE"),
                    b(FIELD_POST_ID), b("TAG"));
            log.info("向量索引 {} 创建成功，DIM={}, DISTANCE_METRIC=COSINE", INDEX_NAME, dim);
        } catch (Exception e) {
            if (isIndexExistsError(e)) {
                log.debug("向量索引 {} 已存在，跳过创建", INDEX_NAME);
                return;
            }
            throw e;
        }
    }

    @Override
    public void upsert(long chunkId, float[] vector, long postId) {
        dispatch("HSET",
                b(KEY_PREFIX + chunkId),
                b(FIELD_EMBEDDING), TextChunker.toBytes(vector),
                b(FIELD_POST_ID), b(Long.toString(postId)));
    }

    @Override
    public void removeByPostId(long postId) {
        // NOCONTENT 使回复不携带 6KB 级的 embedding 二进制字段
        Object reply = dispatchNested("FT.SEARCH",
                b(INDEX_NAME),
                b("@" + FIELD_POST_ID + ":{" + postId + "}"),
                b("NOCONTENT"),
                b("LIMIT"), b("0"), b("1000"));
        int deleted = 0;
        for (String docId : extractDocIds(reply)) {
            if (docId.startsWith(KEY_PREFIX)) {
                dispatch("DEL", b(docId));
                deleted++;
            }
        }
        log.info("已按 post_id={} 删除 {} 条向量文档", postId, deleted);
    }

    @Override
    public List<Hit> topK(float[] queryVector, int k) {
        if (queryVector == null || queryVector.length == 0 || k <= 0) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        // DIALECT 2 支持 KNN 箭头语法；$vec 经 PARAMS 以 blob 传入，避免向量字节被转义破坏
        Object reply = dispatchNested("FT.SEARCH",
                b(INDEX_NAME),
                b("*=>[KNN " + k + " @" + FIELD_EMBEDDING + " $vec AS " + FIELD_SCORE + "]"),
                b("PARAMS"), b("2"), b("vec"), TextChunker.toBytes(queryVector),
                b("DIALECT"), b("2"),
                b("RETURN"), b("2"), b(FIELD_POST_ID), b(FIELD_SCORE));
        List<Hit> hits = parseSearchReply(reply);
        hits.sort(Comparator.comparingDouble(Hit::score));
        log.debug("topK k={} 命中 {} 条，耗时 {} ms", k, hits.size(), System.currentTimeMillis() - start);
        return hits;
    }

    @Override
    public long countDocs() {
        try {
            Object reply = dispatchNested("FT.INFO", b(INDEX_NAME));
            if (!(reply instanceof List<?> rows)) {
                return 0;
            }
            // FT.INFO 返回扁平数组交替 key/value，部分值为嵌套数组，只匹配 num_docs 的后继元素
            for (int i = 0; i + 1 < rows.size(); i++) {
                if ("num_docs".equals(asString(rows.get(i)))) {
                    Long value = parseLongSafe(asString(rows.get(i + 1)));
                    return value == null ? 0 : value;
                }
            }
            return 0;
        } catch (Exception e) {
            // 索引不存在（尚未 ensureIndex）时 FT.INFO 报错，按 0 处理
            log.debug("读取 {} 文档数失败，按 0 处理: {}", INDEX_NAME, e.getMessage());
            return 0;
        }
    }

    // ---- 原始命令派发 ----

    /**
     * 派发返回标量回复的原始命令（FT.CREATE/HSET/DEL 等）。
     * 走 RedisCommands.execute(String, byte[]...)，输出类型由 Spring Data Redis 按命令名推断
     * （HSET→Boolean、DEL→Long、未知 FT.*→byte[]），参数以 byte[] 直传，不经任何序列化器。
     */
    private Object dispatch(String command, byte[]... args) {
        return redisTemplate.execute((RedisCallback<Object>) conn -> conn.execute(command, args));
    }

    /**
     * 派发返回嵌套数组回复的原始命令（FT.SEARCH/FT.INFO）。
     * 默认推断的 ByteArrayOutput 只能承接单个值，无法承载多层结构，因此以 exposeConnection=true
     * 取未代理的原生连接，强转 LettuceConnection 后显式指定 NestedMultiOutput 完整解析嵌套回复。
     */
    private Object dispatchNested(String command, byte[]... args) {
        return redisTemplate.execute((RedisCallback<Object>) conn ->
                ((LettuceConnection) conn).execute(command, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args), true, false);
    }

    // ---- 解析辅助 ----

    /**
     * 解析 FT.SEARCH 回复为命中列表。
     * <p>
     * 回复形态取决于连接协商的 RESP 协议版本，两种均兼容：
     * RESP3：顶层为交替 key/value，结果在 "results" 键下，每个文档是
     * ["id", docId, "extra_attributes", [字段名, 值, ...], "values", [...]] 结构；
     * RESP2：顶层为 [总数, docId, [字段名, 值, ...], docId, [...], ...] 扁平数组。
     */
    private static List<Hit> parseSearchReply(Object reply) {
        List<Hit> hits = new ArrayList<>();
        if (!(reply instanceof List<?> rows) || rows.isEmpty()) {
            return hits;
        }
        if (isResp3Shape(rows)) {
            if (valueAt(rows, "results") instanceof List<?> docs) {
                for (Object doc : docs) {
                    if (!(doc instanceof List<?> docPairs)) {
                        continue;
                    }
                    Long chunkId = parseChunkId(asString(valueAt(docPairs, "id")));
                    Object extra = valueAt(docPairs, "extra_attributes");
                    if (chunkId == null || !(extra instanceof List<?> fields)) {
                        continue;
                    }
                    addHit(hits, chunkId, fields);
                }
            }
        } else {
            for (int i = 1; i + 1 < rows.size(); i += 2) {
                Long chunkId = parseChunkId(asString(rows.get(i)));
                if (chunkId == null || !(rows.get(i + 1) instanceof List<?> fields)) {
                    continue;
                }
                addHit(hits, chunkId, fields);
            }
        }
        return hits;
    }

    /**
     * 提取 FT.SEARCH 回复中的全部 docId（removeByPostId 用，兼容 RESP3/RESP2 形态）。
     */
    private static List<String> extractDocIds(Object reply) {
        List<String> docIds = new ArrayList<>();
        if (!(reply instanceof List<?> rows) || rows.isEmpty()) {
            return docIds;
        }
        if (isResp3Shape(rows)) {
            if (valueAt(rows, "results") instanceof List<?> docs) {
                for (Object doc : docs) {
                    if (doc instanceof List<?> docPairs) {
                        String docId = asString(valueAt(docPairs, "id"));
                        if (docId != null) {
                            docIds.add(docId);
                        }
                    }
                }
            }
        } else {
            for (int i = 1; i < rows.size(); i++) {
                String docId = asString(rows.get(i));
                if (docId != null) {
                    docIds.add(docId);
                }
            }
        }
        return docIds;
    }

    /**
     * 判断 FT.SEARCH 回复是否为 RESP3 map 形态（首元素为 "results" 等 key 而非整数总数）。
     */
    private static boolean isResp3Shape(List<?> rows) {
        return !(rows.get(0) instanceof Long) && valueAt(rows, "results") != null;
    }

    private static void addHit(List<Hit> hits, Long chunkId, List<?> fields) {
        Long postId = parseLongSafe(fieldValue(fields, FIELD_POST_ID));
        Double score = parseDoubleSafe(fieldValue(fields, FIELD_SCORE));
        if (postId != null && score != null) {
            hits.add(new Hit(chunkId, postId, score));
        }
    }

    /**
     * 从交替 key/value 列表中取指定 key 的后继元素，找不到返回 null。
     */
    private static Object valueAt(List<?> pairs, String key) {
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (key.equals(asString(pairs.get(i)))) {
                return pairs.get(i + 1);
            }
        }
        return null;
    }

    /**
     * FT.SEARCH/FT.INFO 回复元素转字符串：byte[] 按 UTF-8 还原，Long 等标量直接 toString。
     */
    private static String asString(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从形如 [字段名, 值, 字段名, 值, ...] 的扁平列表中取指定字段值，找不到返回 null。
     */
    private static String fieldValue(List<?> fields, String name) {
        for (int i = 0; i + 1 < fields.size(); i += 2) {
            if (name.equals(asString(fields.get(i)))) {
                return asString(fields.get(i + 1));
            }
        }
        return null;
    }

    /**
     * 从 docId（kb:chunk:{id}）提取分块ID，格式不符返回 null。
     */
    private static Long parseChunkId(String docId) {
        if (docId == null || !docId.startsWith(KEY_PREFIX)) {
            return null;
        }
        return parseLongSafe(docId.substring(KEY_PREFIX.length()));
    }

    private static Long parseLongSafe(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleSafe(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断异常链中是否为「索引已存在」错误（FT.CREATE 重复执行时 RediSearch 的固定文案），用于幂等。
     */
    private static boolean isIndexExistsError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains("already exists")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] b(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
