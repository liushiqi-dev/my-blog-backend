package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.Mapper.UserMapper;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceimpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Users login(Users users) {
        // 调用Mapper层方法
        return userMapper.getByUsernameAndPassword(users);
    }

    @Override
    public Users register(Users user) {
        // 先检查用户名是否存在
        if (userMapper.existsByUsername(user.getUsername())) {
            return null;
        }
        // 执行注册
        userMapper.insert(user);
        return user;  // 返回包含自增ID的用户对象
    }

}
