package com.liushiqi.blogmain.security.util;

import com.liushiqi.blogmain.entity.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类 - 选用 RS256 非对称加密
 * 【当前方案】使用 jjwt 库生成和解析 JWT Token，采用 RS256 非对称加密
 * 密钥对从 application.yml 读取（Base64编码），保证应用重启后密钥一致
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

    // 从 application.yml 读取的私钥（Base64编码）
    @Value("${jwt.private-key}")
    private String privateKeyBase64;

    // 从 application.yml 读取的公钥（Base64编码）
    @Value("${jwt.public-key}")
    private String publicKeyBase64;

    // 从 application.yml 读取的过期时间（毫秒）
    @Value("${jwt.expiration:604800000}")
    private long expirationTime;

    // 私钥对象
    private PrivateKey privateKey;
    // 公钥对象
    private PublicKey publicKey;

    /**
     * 应用启动时将Base64密钥还原为Java密钥对象
     */
    @PostConstruct
    public void init() throws Exception {
        // 1. 从Base64编码的私钥和公钥还原为字节数组
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);

        // 从字节数组创建密钥工厂
        // 2. 从密钥工厂生成私钥对象
        // 3. 从密钥工厂生成公钥对象
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }


    // 生成 JWT Token
    public String generateJWTToken(Users user) {
        // JJWT 0.12+推荐使用 .claim(key, value) 逐个设置
        // 避免使用 .claims(Map) 时与后续 .subject() 等方法产生覆盖问题
        return Jwts.builder()
                // 1. 设置自定义claims（用户信息）
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                // 2. 设置标准claims
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }


    // 解析 JWT Token
    public Claims parseJWTToken(String token) {
        return Jwts.parser()
                // 1. 验证 JWT 签名，确保 Token 未被篡改
                .verifyWith(publicKey)
                // 2. 解析 JWT Token，提取负载（Claims）
                .build()
                // 3. 从解析结果中获取负载（Claims）
                .parseSignedClaims(token)
                // 4. 返回负载（Claims）
                .getPayload();
    }
}
