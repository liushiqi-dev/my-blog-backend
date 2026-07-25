package com.liushiqi.blogmain.entity;

import java.time.LocalDateTime;

/**
 * categories
 */
@lombok.Data
public class Categories {
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 主键
     */
    private Long id;
    /**
     * 分类名称（2-20字符）
     */
    private String name;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
}