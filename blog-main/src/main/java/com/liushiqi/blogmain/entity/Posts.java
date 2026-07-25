package com.liushiqi.blogmain.entity;

import java.time.LocalDateTime;

/**
 * posts
 */
@lombok.Data
public class Posts {
    /**
     * 作者ID，作者ID
     */
    private Long authorId;
    /**
     * 正文，正文
     */
    private String content;
    /**
     * 创建时间，创建时间
     */
    private LocalDateTime createTime;
    /**
     * ID，主键
     */
    private Long id;
    /**
     * 删除状态，0正常 1已删除
     */
    private Integer isDeleted;
    /**
     * 摘要，正文前100字符
     */
    private String summary;
    /**
     * 标题，标题
     */
    private String title;
    /**
     * 修改时间，修改时间
     */
    private LocalDateTime updateTime;
}