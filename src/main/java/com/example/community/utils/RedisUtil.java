package com.example.community.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis工具类
 */
@Component
public class RedisUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 判断用户是否点赞了文章
     * Key格式: article:like:文章ID -> Set(userId1, userId2, ...)
     */
    public boolean isLiked(Long articleId, Long userId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet()
                        .isMember("article:like:" + articleId, userId.toString())
        );
    }

    /**
     * 点赞文章
     */
    public void like(Long articleId, Long userId) {
        stringRedisTemplate.opsForSet()
                .add("article:like:" + articleId, userId.toString());
    }

    /**
     * 取消点赞
     */
    public void unlike(Long articleId, Long userId) {
        stringRedisTemplate.opsForSet()
                .remove("article:like:" + articleId, userId.toString());
    }
}