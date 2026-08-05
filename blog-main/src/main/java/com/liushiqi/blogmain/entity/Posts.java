package com.liushiqi.blogmain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * posts
 */
@Data
public class Posts {
    /**
     * 作者ID
     */
    private Long authorId;
    /**
     * 正文
     */
    @NotBlank(message = "正文不能为空")
    private String content;
    /**
     * 创建时间
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
     * 摘要，正文前100个字符
     */
    private String summary;
    /**
     * 标题,100个字符
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
    /**
     * 文章状态，PUBLISHED已发布 DRAFT草稿
     */
    private String status;
}