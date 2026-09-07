-- RAG 知识库分块事实源表：博客文章按语义切分后的分块在此持久化，作为向量索引的唯一"事实源"。
-- 设计意图：Redis 向量索引（易失）丢失后可从本表零 token 重建；未来迁移 Milvus 等外部向量库时，
-- 直接读本表 embedding 重灌即可，无需重新调用 embedding 接口。只 CREATE 新表，不 ALTER 任何存量表。
create table kb_chunk
(
    id           bigint auto_increment comment '主键'
        primary key,
    post_id      bigint                             not null comment '所属文章ID，关联 posts.id',
    chunk_index  int                                not null comment '同一文章内的分块序号，从0开始',
    content      text                               not null comment '分块原文，重建向量索引的数据源',
    content_hash char(64)                           not null comment '分块原文的SHA-256十六进制摘要；reindex时未变分块据此跳过embedding以省token',
    embedding    longblob                           null comment '分块向量的FLOAT32小端字节；nullable，允许先入库文本后补向量，索引丢失后据此零token重建',
    create_time  datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    update_time  datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '修改时间',
    constraint uk_post_chunk
        unique (post_id, chunk_index)
)
    comment 'RAG知识库分块事实源表';

create index idx_post_id
    on kb_chunk (post_id);
