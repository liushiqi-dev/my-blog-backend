package com.liushiqi.blogmain.vo;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class PageVo {
    private Integer id;
    private String authorName;
    private String title;
    private String summary;
    private String status;
    private Integer viewCount;
    private LocalDateTime createTime;
}
