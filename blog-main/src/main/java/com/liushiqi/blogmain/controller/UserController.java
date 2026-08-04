package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.response.TokenResponse;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.security.util.JwtUtils;
import com.liushiqi.blogmain.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;


    /**
     * &#064;Valid   注解作用：
     * 1. 自动校验 @RequestBody 传入的参数是否符合实体类中定义的校验规则（如 @NotNull、@NotBlank 等）
     * 2. 校验失败会自动抛出 MethodArgumentNotValidException 异常
     * 3. 避免手写大量 if-else 校验逻辑，代码更简洁
     */
    @PostMapping("login")
    public Result login(@Valid @RequestBody Users user){
        // 参数验证失败会自动抛出异常，由GlobalExceptionHandler处理

        // 调用服务层方法
        Users loggedInUser = userService.login(user);
        if(loggedInUser == null) {
            throw new BusinessException("用户名或密码错误");
        }

        //生成JWT token
        String jwtToken = jwtUtils.generateJWTToken(loggedInUser);
        return Result.success(new TokenResponse(jwtToken));
    }

    @PostMapping("register")
    public Result register(@Valid @RequestBody Users user){
        // 参数验证失败会自动抛出异常，由GlobalExceptionHandler处理

        Users registeredUser = userService.register(user);
        // 注册成功后生成JWT token，直接登录
        String jwtToken = jwtUtils.generateJWTToken(registeredUser);
        return Result.success(new TokenResponse(jwtToken));
    }

    //获取用户信息
    @GetMapping("/me")
    public Result getUserInfo(){
        // 从SecurityContext中获取当前登录用户
        UserVO userInfo = userService.getUserInfo();
        return Result.success(userInfo);
    }
}