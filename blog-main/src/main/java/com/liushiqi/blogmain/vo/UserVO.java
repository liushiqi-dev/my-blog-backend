package com.liushiqi.blogmain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象（返回给前端）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {
    private String username;
    private String role;
    private String email;
}
