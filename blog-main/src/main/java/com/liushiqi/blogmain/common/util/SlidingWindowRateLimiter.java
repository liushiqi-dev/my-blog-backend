package com.liushiqi.blogmain.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口限流器（ZSet + Lua 原子版）
 * <p>
 * 每次请求以时间戳为 score 存入 ZSet，窗口外的记录原子清除，
 * 窗口内请求数超过阈值则拒绝。相比固定窗口，消除了窗口边界的突发问题。
 */
@Slf4j
@Component
public class SlidingWindowRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Boolean> slidingWindowScript;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sliding_window_rate_limit.lua"))
        );
        slidingWindowScript.setResultType(Boolean.class);
    }

    /**
     * 尝试获取一次调用资格
     * @param key 限流维度键，如 rate:login:{ip}
     * @param threshold 窗口内最大放行次数
     * @param windowSeconds 窗口长度（秒）
     * @return true=放行 false=超限拒绝
     */
    public boolean tryAcquire(String key, int threshold, int windowSeconds) {
        try {
            long now = System.currentTimeMillis();
            long windowMs = windowSeconds * 1000L;
            String memberId = now + ":" + UUID.randomUUID().toString().substring(0, 8);

            Boolean success = redisTemplate.execute(
                    slidingWindowScript,
                    List.of(key),
                    memberId, String.valueOf(now), String.valueOf(windowMs), String.valueOf(threshold)
            );
            return success != null && success;
        } catch (DataAccessException e) {
            log.warn("限流器 Redis 不可用，fail-open 放行, key={}", key, e);
            return true;
        }
    }
}