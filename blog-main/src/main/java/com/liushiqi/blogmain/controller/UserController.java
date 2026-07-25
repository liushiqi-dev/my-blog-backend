package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.Result;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("login")
    public Result login(Users user){
        // 调用服务层方法
        Users t=userService.login(user);
        if(user.getUsername()==null||user.getPassword()==null)
            return Result.error("用户名或密码不能为空");
        if(t==null)
            return Result.error("用户名或密码错误");
        //生成JWT token
        String jwtToken= JwtUtils.generateJWTToken(t);
        return Result.success(jwtToken);
    }
}