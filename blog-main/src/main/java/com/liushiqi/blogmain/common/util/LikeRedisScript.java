package com.liushiqi.blogmain.common.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LikeRedisScript {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> toggleLikeScript;

    public LikeRedisScript(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;

        toggleLikeScript = new DefaultRedisScript<>();
        toggleLikeScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/toggle_like.lua"))
        );
        toggleLikeScript.setResultType(Long.class);
    }

    /**
     * 切换点赞状态
     * @return 1点赞 0取消点赞
     */
    public Long toggleLike(Long postId, Long userId) {
        return redisTemplate.execute(
                toggleLikeScript,
                List.of("post:likes:" + postId, "post:likeCount:" + postId),
                userId.toString()
        );
    }
}