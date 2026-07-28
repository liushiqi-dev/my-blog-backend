package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.Mapper.UserMapper;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserServiceimpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // 登录
    @Override
    public Users login(Users users) {
        // 根据用户名查询用户
        Users dbUser = userMapper.findByUsername(users.getUsername());

        if (dbUser == null) {
            return null;  // 用户不存在
        }

        // 验证密码
        if (!passwordEncoder.matches(users.getPassword(), dbUser.getPassword())) {
            return null;  // 密码错误
        }

        return dbUser;
    }

    // 注册
    @Override
    public Users register(Users user) {
        // 先检查用户名是否存在
        if (userMapper.existsByUsername(user.getUsername())) {
            return null;
        }

        // 加密密码
        String encryptedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
        user.setRole("USER");  // 默认角色为 USER

        // 执行注册
        userMapper.insert(user);
        return user;  // 返回包含自增ID的用户对象
    }

    // 获取用户信息
    @Override
    public Users getUserInfo() {
        // 从 SecurityContext 中获取当前登录用户ID
        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        // 根据ID查询用户信息
        return userMapper.findById(userId);
    }
}
