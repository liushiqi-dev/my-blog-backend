package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.vo.AskVo;

/**
 * RAG 知识库业务接口：问答与索引重建
 */
public interface KbService {

    /**
     * 基于已发布文章的知识库问答：问题向量化 → KNN 召回分块 → 拼接上下文 → LLM 生成回答
     *
     * @param question 用户问题
     * @return 回答与引用来源；召回为空时返回降级提示
     */
    AskVo ask(String question);

    /**
     * 重建知识库索引：全量已发布文章分块、增量向量化，MySQL 与 Redis 双写
     *
     * @return 本次索引覆盖的分块总数
     */
    int reindex();
}
