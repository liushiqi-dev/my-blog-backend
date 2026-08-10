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
    private String authorName;
    private String title;
    private String content;
    private String summary;
    private String status;
    private String categoryNames;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
