package com.liushiqi.blogmain.security.util;

import com.liushiqi.blogmain.entity.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 - 选用 RS256 非对称加密

 * 【当前方案】使用 jjwt 库生成和解析 JWT Token，采用 RS256 非对称加密

 * 【选型理由】
 * 1. RS256 是企业级 JWT 签名算法首选，安全性高于 HS256
 * 2. 私钥签名、公钥验证，私钥只在认证服务保存，即使 Token 泄露也无法伪造
 * 3. 适合分布式系统，公钥可分发给其他服务验证 Token，无需共享密钥
 * 4. 符合 OAuth2、OpenID Connect 等企业级安全规范

 * 【备选方案】
 * 1. HS256：对称加密，性能高但安全性较低，适合单体应用
 * 2. ES256：椭圆曲线加密，密钥更短但性能较低

 * 【不选理由】
 * - 不选 HS256：本项目目标是企业级项目模版，RS256 安全性更高
 * - 不选 ES256：性能较低，企业级采用率不如 RS256
 */
@Component
public class JwtUtils {

    private static final KeyPair KEY_PAIR = Jwts.SIG.RS256.keyPair().build();

    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;

    public static String generateJWTToken(Users user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());  // 添加角色信息

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                // 设置签发时间为当前时间
                .issuedAt(new Date())
                // 设置过期时间为当前时间+7天
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public static Claims parseJWTToken(String token) {
        return Jwts.parser()//
                // 验证签名
                .verifyWith(KEY_PAIR.getPublic())
                // 验证过期时间
                .build()
                // 解析 JWT Token
                .parseSignedClaims(token)
                // 获取 payload 部分
                .getPayload();
    }

    // 验证 JWT Token
    // 如果 Token 有效，返回 true；否则返回 false
    public static boolean validateToken(String token) {
        try {
            parseJWTToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 获取公钥
    public static String getPublicKey() {
        return KEY_PAIR.getPublic().toString();
    }
}