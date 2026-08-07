package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.request.LoginRequest;
import com.liushiqi.blogmain.dto.request.RegisterRequest;
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


    /*
      @Valid   注解作用：
      1. 自动校验 @RequestBody 传入的参数是否符合实体类中定义的校验规则（如 @NotNull、@NotBlank 等）
      2. 校验失败会自动抛出 MethodArgumentNotValidException 异常
      3. 避免手写大量 if-else 校验逻辑，代码更简洁
     */
    @PostMapping("login")
    public Result login(@Valid @RequestBody LoginRequest req){
        // 参数验证失败会自动抛出异常，由GlobalExceptionHandler处理
        Users loggedInUser = userService.login(req);

        String jwtToken = jwtUtils.generateJWTToken(loggedInUser);
        return Result.success(new TokenResponse(jwtToken));
    }

    @PostMapping("register")
    public Result register(@Valid @RequestBody RegisterRequest req){
        Users registeredUser = userService.register(req);
        // 注册成功后生成JWT token，直接登录
        String jwtToken = jwtUtils.generateJWTToken(registeredUser);
        return Result.success(new TokenResponse(jwtToken));
    }

    @GetMapping("/me")
    public Result getUserInfo(){
        UserVO userInfo = userService.getUserInfo();
        return Result.success(userInfo);
    }
}