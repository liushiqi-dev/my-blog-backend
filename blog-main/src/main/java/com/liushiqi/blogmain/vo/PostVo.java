package com.liushiqi.blogmain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostVo {
    private Long id;
    private String authorName;
    private String title;
    private String content;
    private String summary;
    private String status;
    private String categoryNames;
    /** 浏览量（Redis 实时累计，定时任务异步落库） */
    private Integer viewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
