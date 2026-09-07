package com.liushiqi.blogmain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * kb_chunk：RAG 知识库分块事实源实体
 * <p>
 * 博客文章按语义切分后的每个分块对应一条记录，持久化分块原文与向量字节，
 * 作为 Redis 向量索引（易失）丢失后零 token 重建、以及未来迁移外部向量库重灌的事实源。
 */
@Data
public class KbChunk {
    /**
     * ID，主键
     */
    private Long id;
    /**
     * 所属文章ID，关联 posts.id
     */
    private Long postId;
    /**
     * 同一文章内的分块序号，从0开始
     */
    private Integer chunkIndex;
    /**
     * 分块原文，重建向量索引的数据源
     */
    private String content;
    /**
     * 分块原文的 SHA-256 十六进制摘要；reindex 时未变分块据此跳过 embedding 以省 token
     */
    private String contentHash;
    /**
     * 分块向量的 FLOAT32 小端字节；可为空，允许先入库文本后补向量
     */
    private byte[] embedding;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
}
