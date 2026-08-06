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

    @Override
    public Users login(Users users) {
        Users dbUser = userMapper.findByUsername(users);

        if (dbUser == null) {
            return null;
        }

        if (!passwordEncoder.matches(users.getPassword(), dbUser.getPassword())) {
            return null;
        }

        return dbUser;
    }

    @Override
    public Users register(Users user) {
        if (userMapper.existsByUsername(user.getUsername())) {
            throw new BusinessException("用户名已被占用");
        }

        if (user.getEmail() != null && userMapper.existsByEmail(user.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        String encryptedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
        // 显式设置默认角色（数据库虽有默认值，但显式设置更明确）
        user.setRole("USER");

        userMapper.insert(user);
        // insert 后 user.id 已由 useGeneratedKeys 回填
        return user;
    }

    @Override
    public UserVO getUserInfo() {
        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        Users user = userMapper.findById(userId);
        return new UserVO(user.getUsername(), user.getRole(), user.getEmail());
    }
}