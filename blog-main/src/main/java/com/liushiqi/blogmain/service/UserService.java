package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.vo.UserVO;

/**
 * 用户业务逻辑接口
 */
public interface UserService {
    Users login(Users users);

    Users register(Users user);

    UserVO getUserInfo();
}
