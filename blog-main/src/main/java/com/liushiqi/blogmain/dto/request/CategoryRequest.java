package com.liushiqi.blogmain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 2, max = 20, message = "分类名称应为2-20字符")
    private String name;
}
