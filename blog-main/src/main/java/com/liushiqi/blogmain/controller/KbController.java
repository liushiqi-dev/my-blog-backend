package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.request.AskRequest;
import com.liushiqi.blogmain.service.KbService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 知识库问答接口（context-path=/api，实际路径 /api/kb/**）
 */
@Slf4j
@RestController
@RequestMapping("/kb")
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    /**
     * 知识库问答，对匿名用户开放
     */
    @PostMapping("/ask")
    public Result ask(@Valid @RequestBody AskRequest req) {
        return Result.success(kbService.ask(req.getQuestion()));
    }

    /**
     * 重建知识库索引，仅管理员可触发（一次重建会产生批量 embedding 调用）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reindex")
    public Result reindex() {
        int chunks = kbService.reindex();
        log.info("知识库索引重建完成，分块数：{}", chunks);
        return Result.success(chunks);
    }
}
