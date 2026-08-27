package com.liushiqi.blogmain.config;

import com.liushiqi.blogmain.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 * 方法级权限控制说明：
 * 1. @EnableMethodSecurity 启用方法级安全注解（@PreAuthorize, @PostAuthorize等）
 * 2. 推荐在Controller层使用 @PreAuthorize("hasRole('ADMIN')") 进行权限校验
 * 3. 相比URL级权限，方法级权限更灵活，可结合业务逻辑进行复杂判断
 * 4. 符合"快速失败"原则，在入口处拦截无权限请求，避免进入业务层
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置安全过滤器链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http// 配置请求授权
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
                // 配置跨域
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 配置会话管理
            .sessionManagement(session -> session
                    // 无状态 Session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
                // 异常处理：未登录访问受保护接口时返回HTTP 401 + 统一JSON格式
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":2,\"message\":\"未登录\",\"data\":null}");
                })
            )
            .csrf(AbstractHttpConfigurer::disable)  // 禁用 CSRF（前后端分离）
            .formLogin(AbstractHttpConfigurer::disable);  // 禁用默认登录页

        return http.build();
    }

    /**
     * 跨域配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));  // 允许的域名。todo 仅为开发环境配置
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE"));  // 允许的方法
        configuration.setAllowedHeaders(List.of("*"));  // 允许的头
        configuration.setAllowCredentials(true);  // 允许携带凭证

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);  // 对所有路径生效

        return source;
    }

    /**
     * 密码加密器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}