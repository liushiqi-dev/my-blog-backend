package com.liushiqi.blogmain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库问答请求DTO
 */
@Data
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500个字符")
    private String question;
}
