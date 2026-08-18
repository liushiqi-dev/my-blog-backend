package com.liushiqi.blogmain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 配置类
 * 本配置类做了什么？
 * 1. Key   → StringRedisSerializer      ：Key 用纯字符串序列化，存进去就是 "user:1" 而不是乱码
 * 2. Value → GenericJacksonJsonRedisSerializer ：Value 用 JSON 序列化，存进去就是 {"id":1,"name":"张三"}
 * <p>
 * ⚠️ 为什么不用 GenericJackson2JsonRedisSerializer？
 * 它在 Spring Data Redis 4.0 中已被 @Deprecated 标记为废弃，原因是：
 * - 底层依赖的 Jackson 2 已升级为 Jackson 3，命名从 com.fasterxml → tools.jackson
 * - 自动嵌入 @class 类型信息导致 JSON 冗余，且存在反序列化安全风险
 * <p>
 * GenericJacksonJsonRedisSerializer 的两种模式：
 * - 默认模式：不保存类型信息，JSON 更干净，但反序列化时需要手动指定目标类型
 * - enableUnsafeDefaultTyping()：保存类型信息，反序列化时自动还原类型，但存在安全风险
 *   这里选择开启，因为博客项目数据来源可控，且需要自动还原类型的便利性
 * <p>
 * 💡 八股文关联：
 * - Redis 底层存储的都是 byte[]，序列化器负责 Java 对象 ↔ byte[] 的转换
 * - StringRedisTemplate 自带 StringRedisSerializer，只能存字符串，适合简单场景
 * - RedisTemplate<K,V> 是泛型的，需要自己配序列化器，适合存对象
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate，覆盖 Spring Boot 默认的
     *
     * @param connectionFactory Redis 连接工厂，Spring Boot 根据 application-dev.yaml 中的配置自动创建
     * @return 配置好序列化器的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 设置连接工厂（必须，否则无法连接 Redis）
        template.setConnectionFactory(connectionFactory);

        // ---- 序列化器配置 ----

        // String 序列化器：将字符串直接转为 UTF-8 字节，不做任何额外处理
        // 适合用来序列化 Key，因为 Key 通常是简单字符串（如 "user:1"、"post:list"）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // JSON 序列化器：将 Java 对象转为 JSON 字符串存储
        // 使用 GenericJacksonJsonRedisSerializer（Jackson 3 版本），反序列化时能自动识别类型
        GenericJacksonJsonRedisSerializer jsonSerializer = createJsonSerializer();

        // ---- Key 的序列化配置 ----
        // Key 用 String 序列化器，保证 key 存储为可读字符串
        template.setKeySerializer(stringSerializer);
        // Hash 的 Key 也用 String 序列化器
        template.setHashKeySerializer(stringSerializer);

        // ---- Value 的序列化配置 ----
        // Value 用 JSON 序列化器，保证 value 存储为可读 JSON
        template.setValueSerializer(jsonSerializer);
        // Hash 的 Value 也用 JSON 序列化器
        template.setHashValueSerializer(jsonSerializer);

        // 初始化配置，让设置生效
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 创建 JSON 序列化器（基于 Jackson 3）
     * 使用 Builder 模式创建 GenericJacksonJsonRedisSerializer：
     * 1. builder(() -> JsonMapper.builder())：传入 Jackson 3 的 JsonMapper 构建器工厂
     *    - JsonMapper 是 Jackson 3 中 ObjectMapper 的子类，专门用于 JSON 格式
     *    - 传入构建器工厂，让 Spring Data Redis 内部创建并配置 JsonMapper
     * 2. enableUnsafeDefaultTyping()：在 JSON 中保存 @class 类型信息
     *    - "Unsafe" 是因为任意类型都能被反序列化，存在潜在安全风险
     *    - 但对于内部使用的缓存场景（数据来源可控），这是可接受的
     *    - 开启后 JSON 中会包含：{"@class":"com.liushiqi.blogmain.entity.Users","id":1,...}
     *    - 不开启的话，反序列化只能得到 LinkedHashMap，需要手动转换类型
     */
    private GenericJacksonJsonRedisSerializer createJsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder(JsonMapper::builder)
                .enableUnsafeDefaultTyping()
                .build();
    }
}