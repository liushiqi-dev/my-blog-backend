package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.mapper.UserMapper;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.vo.UserVO;
import com.liushiqi.blogmain.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // 登录
    @Override
    public Users login(Users users) {
        // 根据用户名查询用户
        Users dbUser = userMapper.findByUsername(users);

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
        // 检查用户名是否存在
        if (userMapper.existsByUsername(user.getUsername())) {
            throw new BusinessException("用户名已被占用");
        }

        // 检查邮箱是否存在
        if (user.getEmail() != null && userMapper.existsByEmail(user.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        // 加密密码
        String encryptedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
//        user.setRole("USER");  // 默认角色为 USER//数据库已有默认角色USER

        // 执行注册
        userMapper.insert(user);
        return user;  // 返回包含自增ID的用户对象
    }

    // 获取用户信息
    @Override
    public UserVO getUserInfo() {
        // 从 SecurityContext 中获取当前登录用户ID
        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        // 根据ID查询用户信息
        Users user = userMapper.findById(userId);
        // 转换为VO，只返回前端需要的字段
        return new UserVO(user.getUsername(), user.getRole(), user.getEmail());
    }
}