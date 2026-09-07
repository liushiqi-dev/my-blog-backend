package com.liushiqi.blogmain.kb;

import java.util.List;

/**
 * Redis 8 HNSW 向量检索层抽象：管理 kb_idx 索引生命周期，以及分块向量的写入、删除与 KNN 近邻查询。
 * <p>
 * 向量事实源在 kb_chunk 表，本层只承载易失的检索结构；向量以 FLOAT32 小端字节写入 HASH，
 * 维度统一取自 rag.embedding-dimension，保证建索引（DIM）、写入、查询三者一致。
 */
public interface VectorSearcher {

    /**
     * 确保 kb_idx 索引存在；已存在时不重复创建（幂等），供应用启动与重建流程调用。
     */
    void ensureIndex();

    /**
     * 写入或覆盖一个分块向量文档（key 为 kb:chunk:{chunkId}，hash 字段 embedding 存向量字节、post_id 存 TAG）。
     *
     * @param chunkId 分块ID，对应 kb_chunk.id
     * @param vector  与索引 DIM 等长的浮点向量
     * @param postId  所属文章ID
     */
    void upsert(long chunkId, float[] vector, long postId);

    /**
     * 删除指定文章在索引中的全部向量文档，用于文章删除或重新索引前清理旧分块。
     *
     * @param postId 文章ID
     */
    void removeByPostId(long postId);

    /**
     * KNN 近邻检索，返回按 COSINE 距离升序（越小越相似）的前 k 条命中。
     *
     * @param queryVector 查询向量，维度须与索引 DIM 一致
     * @param k           返回条数上限
     * @return 命中列表，相似度从高到低排列
     */
    List<Hit> topK(float[] queryVector, int k);

    /**
     * 返回索引中的文档数；索引不存在时返回 0。
     */
    long countDocs();
}
