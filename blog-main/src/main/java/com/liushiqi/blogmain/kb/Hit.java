package com.liushiqi.blogmain.kb;

/**
 * 一次 KNN 向量检索的单条命中结果。
 *
 * @param chunkId 分块ID，对应 Redis key kb:chunk:{chunkId} 与 kb_chunk.id
 * @param postId  所属文章ID
 * @param score   相似度分：KNN 返回的 COSINE 距离，值越小越相似，0 表示方向完全一致
 */
public record Hit(long chunkId, long postId, double score) {
}
