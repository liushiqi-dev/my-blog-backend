package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.common.util.SlidingWindowRateLimiter;
import com.liushiqi.blogmain.dto.request.LoginRequest;
import com.liushiqi.blogmain.dto.request.RegisterRequest;
import com.liushiqi.blogmain.dto.response.TokenResponse;
import com.liushiqi.blogmain.entity.Users;
import com.liushiqi.blogmain.service.UserService;
import com.liushiqi.blogmain.security.util.JwtUtils;
import com.liushiqi.blogmain.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    /** 登录限流：同一 IP 每 60 秒窗口内最多放行 5 次尝试，防暴力破解/撞库 */
    private static final int LOGIN_LIMIT_THRESHOLD = 5;
    private static final int LOGIN_LIMIT_WINDOW_SECONDS = 60;

    /** 注册限流：同一 IP 每 60 秒窗口内最多放行 3 次，防批量建号 */
    private static final int REGISTER_LIMIT_THRESHOLD = 3;
    private static final int REGISTER_LIMIT_WINDOW_SECONDS = 60;


    /*
      @Valid   注解作用：
      1. 自动校验 @RequestBody 传入的参数是否符合实体类中定义的校验规则（如 @NotNull、@NotBlank 等）
      2. 校验失败会自动抛出 MethodArgumentNotValidException 异常
      3. 避免手写大量 if-else 校验逻辑，代码更简洁
     */
    @PostMapping("login")
    public Result login(@Valid @RequestBody LoginRequest req, HttpServletRequest request){
        // 滑动窗口限流：按客户端 IP 限制尝试频率；校验放在密码校验之前——bcrypt 是 CPU 大户，拒绝要在算哈希前完成
        if (!slidingWindowRateLimiter.tryAcquire("rate:login:" + getClientIp(request),
                LOGIN_LIMIT_THRESHOLD, LOGIN_LIMIT_WINDOW_SECONDS)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        // 参数验证失败会自动抛出异常，由GlobalExceptionHandler处理
        Users loggedInUser = userService.login(req);
        String jwtToken = jwtUtils.generateJWTToken(loggedInUser);
        log.info("登录成功，用户ID：{}", loggedInUser.getId());
        return Result.success(new TokenResponse(jwtToken));
    }

    @PostMapping("register")
    public Result register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request){
        // 滑动窗口限流：与登录各自独立计数（key 前缀不同），互不牵连
        if (!slidingWindowRateLimiter.tryAcquire("rate:register:" + getClientIp(request),
                REGISTER_LIMIT_THRESHOLD, REGISTER_LIMIT_WINDOW_SECONDS)) {
            throw new BusinessException("注册操作过于频繁，请稍后再试");
        }
        Users registeredUser = userService.register(req);
        // 注册成功后生成JWT token，直接登录
        String jwtToken = jwtUtils.generateJWTToken(registeredUser);
        log.info("注册成功，用户ID：{}", registeredUser.getId());
        return Result.success(new TokenResponse(jwtToken));
    }

    @GetMapping("/me")
    public Result getUserInfo(){
        UserVO userInfo = userService.getUserInfo();
        log.info("获取用户信息，用户名：{}", userInfo.getUsername());
        return Result.success(userInfo);
    }

    /**
     * 获取客户端真实 IP
     * <p>
     * 优先级：X-Real-IP → X-Forwarded-For 首段 → remoteAddr
     * <p>
     * X-Real-IP 优先的原因：nginx 的 proxy_set_header 是覆盖写入，客户端无法伪造；
     * X-Forwarded-For 是追加写入，首段可能被客户端伪造，仅作兜底。
     */
    private String getClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}