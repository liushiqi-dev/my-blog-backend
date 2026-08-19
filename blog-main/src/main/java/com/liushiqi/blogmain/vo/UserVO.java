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
public class UserVO {//fixme:末尾为VO Vo待商榷
    private String username;
    private String role;
    private String email;
    private Long totalLikes;
    private Long totalViews;
}
