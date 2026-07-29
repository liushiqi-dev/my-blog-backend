package com.liushiqi.blogmain.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * users
 */
@Data
//@AllArgsConstructor
//@NoArgsConstructor
public class Users {
    /**
     * 创建时间，用户账号创建的时间戳
     */
    private LocalDateTime createTime;
    /**
     * 电子邮箱，用户的联系邮箱地址
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    /**
     * 用户ID，主键，唯一标识用户的数字ID
     */
    private Long id;
    /**
     * 密码，使用BCrypt算法加密的用户密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在6-32个字符之间")
    private String password;
    /**
     * 角色，ADMIN 或 USER
     */
    private String role;
    /**
     * 更新时间，用户账号最后更新的时间戳
     */
    private LocalDateTime updateTime;
    /**
     * 用户名，用户登录名，长度不超过20个字符
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20个字符之间")
    private String username;
}
