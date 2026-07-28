package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.entity.Users;

public interface UserService {
    // 登录
    Users login(Users users);

    // 注册: 返回用户对象(包含ID)表示成功，null表示用户名已存在
    Users register(Users user);

    Users getUserInfo();
}
