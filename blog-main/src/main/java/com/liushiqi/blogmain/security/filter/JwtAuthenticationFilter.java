package com.liushiqi.blogmain.security.filter;

import com.liushiqi.blogmain.security.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 从请求头获取 token
        String token = request.getHeader("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);  // 去掉 "Bearer " 前缀

            try {
                // 解析 token 并提取用户信息
                Claims claims = jwtUtils.parseJWTToken(token);
                // JWT的Payload是JSON格式，数字默认被解析为Integer而非Long
                // 用Map.get获取原始值再强转Number，避免Claims.get(key, Class)类型不匹配返回null
                Long userId = ((Number) claims.get("userId")).longValue();
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);

                // 创建权限列表
                // Spring Security要求角色权限以ROLE_开头
                // hasRole('ADMIN') 实际检查的是 ROLE_ADMIN
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

                // 创建认证对象并存入 SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // JWT 解析失败（签名错误/过期/格式错误）：清除安全上下文
                // 确保无效 Token 不会获得任何权限，由后续 SecurityConfig 规则返回 401/403
                SecurityContextHolder.clearContext();
                logger.warn("JWT token 验证失败: " + e.getMessage());
            }
        }

        // 放行
        filterChain.doFilter(request, response);
    }
}
