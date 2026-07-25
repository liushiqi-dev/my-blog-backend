package com.liushiqi.blogmain.interceptor;

import com.liushiqi.blogmain.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws IOException {
        String token = request.getHeader("Authorization");

        //验证Token是否存在且格式正确
        if (token == null || !token.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":1000,\"message\":\"请先登录\"}");
            return false;
        }

        // 验证JWT是否有效
        String jwtToken = token.substring(7);
        if (!JwtUtils.validateToken(jwtToken)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":1001,\"message\":\"Token无效或已过期\"}");
            return false;
        }

        // 从JWT中获取用户ID和用户名，并将其存储到请求属性中。存储后整个请求过程中都可以使用这些属性
        Claims claims = JwtUtils.parseJWTToken(jwtToken);
        request.setAttribute("userId", claims.get("userId", Integer.class));
        request.setAttribute("username", claims.get("username", String.class));

        return true;
    }
}