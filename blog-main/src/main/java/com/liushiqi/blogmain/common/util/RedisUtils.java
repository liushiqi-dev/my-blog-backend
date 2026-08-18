package com.liushiqi.blogmain.common.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Redis 工具类
 * <p>
 * 封装 Redis 常用操作，避免在 Service 层重复编写 scan、随机 TTL 等模板代码。
 * 通过 @Component 注入 Spring 容器，任何 Service 都可以直接 @Autowired 使用。
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 使用 scan 渐进式扫描并删除匹配 pattern 的所有 Key
     * <p>
     * scan vs keys 的区别：
     * - keys("posts:*")  ：一次性扫描所有 Key，O(N)，Key 多时会阻塞 Redis
     * - scan(...)        ：分批扫描，每次只返回一小批结果，不会阻塞 Redis
     * <p>
     * scan 的工作原理：
     * 1. 调用 SCAN 0 MATCH "posts:*" COUNT 100，返回游标 + 一批 Key
     * 2. 如果游标不为 0，继续用新游标调用 SCAN，直到游标回到 0（扫描完毕）
     * 3. Spring 的 RedisTemplate.scan() 封装了整个迭代过程，返回一个 Cursor
     * 4. 我们遍历 Cursor 收集所有 Key，然后一次性删除
     */
    public void deleteByPattern(String pattern) {
        Set<String> keys = StreamSupport.stream(
                        redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(100).build()).spliterator(),
                        false)
                .map(Object::toString)
                .collect(Collectors.toSet());
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 带随机偏移的缓存写入，防止大量 Key 同时过期导致缓存雪崩
     *
     * @param key      缓存 Key
     * @param value    缓存 Value
     * @param base     基础过期时间
     * @param offset   随机偏移范围（实际 TTL = base ± offset）
     * @param unit     时间单位
     */
    public void setWithRandomTtl(String key, Object value, long base, long offset, TimeUnit unit) {
        long ttl = base + ThreadLocalRandom.current().nextLong(-offset, offset + 1);
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }
}