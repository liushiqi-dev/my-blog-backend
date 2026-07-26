package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.Result;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("login")
    public Result login(@RequestBody Users user){
        // 参数验证: 对于绕过前端的异常请求,统一返回模糊错误信息
        if(user.getUsername()==null||user.getUsername().trim().isEmpty()||
           user.getPassword()==null||user.getPassword().trim().isEmpty()||
           user.getUsername().length()<3||user.getUsername().length()>20||
           user.getPassword().length()<6||user.getPassword().length()>32){
            return Result.error("用户名或密码错误");
        }

        // 调用服务层方法
        Users loggedInUser=userService.login(user);
        if(loggedInUser==null)
            return Result.error("用户名或密码错误");

        //生成JWT token
        String jwtToken= JwtUtils.generateJWTToken(loggedInUser);
        return Result.success(jwtToken);
    }

    @PostMapping("register")
    public Result register(@RequestBody Users user){
        //参数校验
        if(user.getUsername()==null||user.getUsername().trim().isEmpty()||
           user.getPassword()==null||user.getPassword().trim().isEmpty()||
           user.getUsername().length()<3||user.getUsername().length()>20||
           user.getPassword().length()<6||user.getPassword().length()>32){
            return Result.error("用户名或密码格式错误");
        }

        Users registeredUser = userService.register(user);
        if (registeredUser == null) {
            return Result.error("用户名已被占用");
        }
        // 注册成功后生成JWT token，直接登录
        String jwtToken = JwtUtils.generateJWTToken(registeredUser);
        return Result.success(jwtToken);
    }
}