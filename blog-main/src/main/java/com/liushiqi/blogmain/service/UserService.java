package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.dto.request.LoginRequest;
import com.liushiqi.blogmain.dto.request.RegisterRequest;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.vo.UserVO;

/**
 * 用户业务逻辑接口
 */
public interface UserService {
    Users login(LoginRequest req);

    Users register(RegisterRequest req);

    UserVO getUserInfo();
}
