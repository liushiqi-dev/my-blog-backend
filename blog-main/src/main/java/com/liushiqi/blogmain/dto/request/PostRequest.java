package com.liushiqi.blogmain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 文章发布/更新请求DTO
 * id 仅在更新时由 Controller 从路径参数设置
 */
@Data
public class PostRequest {
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "正文不能为空")
    private String content;

    @NotEmpty(message = "分类不能为空")
    private List<Integer> categoryIds;

    private String summary;
}
