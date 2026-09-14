package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.dto.request.RegisterRequest;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userServiceImpl;

    @Test
    void registerSuccess() {
        // 1. 准备（Arrange）：构造请求参数
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("123456");
        req.setEmail("test@example.com");

        // 2. 打桩（Stub）：替 Mock 对象回答问题
        when(userMapper.existsByUsername("testuser")).thenReturn(false);
        when(userMapper.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encodedPwd");

        // 3. 执行（Act）：调用被测方法
        Users result = userServiceImpl.register(req);

        // 4. 断言（Assert）：验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("encodedPwd", result.getPassword());
        verify(userMapper).insert(any(Users.class));
    }

    @Test
    void registerUsernameTaken() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("123456");

        // Stub：这次让"用户名已存在"返回 true —— 走进异常分支
        when(userMapper.existsByUsername("testuser")).thenReturn(true);

        // Act + Assert 合并：断言"调用 register 时会抛 BusinessException"
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userServiceImpl.register(req));

        assertEquals("用户名已被占用", ex.getMessage());
        // 验证注册失败时绝不写库
        verify(userMapper, never()).insert(any(Users.class));
    }

    @Test
    void registerEmailTaken(){
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("123456");
        req.setEmail("test@example.com");

        // Stub：这次让"邮箱已存在"返回 true —— 走进异常分支
        when(userMapper.existsByUsername("testuser")).thenReturn(false);
        when(userMapper.existsByEmail("test@example.com")).thenReturn(true);

        // Act + Assert 合并：断言"调用 register 时会抛 BusinessException"
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userServiceImpl.register(req));

        assertEquals("邮箱已被注册", ex.getMessage());
    }

    @Test
    void registerSuccessWithoutEmail() {
        // Arrange：不填邮箱（email 为 null，走 && 短路路径）
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("123456");

        // Stub：email 为 null 时短路发生，existsByEmail 不会被调用，所以不给它打桩
        when(userMapper.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encodedPwd");

        // Act
        Users result = userServiceImpl.register(req);

        // Assert
        assertNotNull(result);
        assertNull(result.getEmail());
        verify(userMapper).insert(any(Users.class));
        // 验证短路确实发生：existsByEmail 从未被调用
        verify(userMapper, never()).existsByEmail(any());
    }
}