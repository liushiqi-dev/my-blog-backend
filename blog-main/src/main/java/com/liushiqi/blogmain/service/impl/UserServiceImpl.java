package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.dto.request.LoginRequest;
import com.liushiqi.blogmain.dto.request.RegisterRequest;
import com.liushiqi.blogmain.mapper.UserMapper;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.vo.UserVO;
import com.liushiqi.blogmain.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public Users login(LoginRequest req) {
        Users dbUser = userMapper.findByUsername(req.getUsername());

        if (dbUser == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(req.getPassword(), dbUser.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        return dbUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Users register(RegisterRequest req) {
        if (userMapper.existsByUsername(req.getUsername())) {
            throw new BusinessException("用户名已被占用");
        }

        if (req.getEmail() != null && userMapper.existsByEmail(req.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        Users user = new Users();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        // 显式设置默认角色（数据库虽有默认值，但显式设置更明确）
        user.setRole("USER");

        userMapper.insert(user);
        // insert 后 user.id 已由 useGeneratedKeys 回填
        return user;
    }

    @Override
    public UserVO getUserInfo() {
        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        return userMapper.findById(userId);
    }
}