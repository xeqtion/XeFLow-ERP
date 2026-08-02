package com.erp.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的登录态存储 (默认; 适合多实例 / 云端横向扩展)。
 */
@Component
@ConditionalOnProperty(name = "erp.session.store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenStore implements TokenStore {

    private static final String LOGIN_TOKEN_KEY = "erp:login:token:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTokenStore(@Qualifier("sessionRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String token, LoginUser loginUser, long ttlMinutes) {
        redisTemplate.opsForValue().set(LOGIN_TOKEN_KEY + token, loginUser, ttlMinutes, TimeUnit.MINUTES);
    }

    @Override
    public LoginUser get(String token) {
        Object value = redisTemplate.opsForValue().get(LOGIN_TOKEN_KEY + token);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

    @Override
    public void remove(String token) {
        redisTemplate.delete(LOGIN_TOKEN_KEY + token);
    }

    @Override
    public void removeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(LOGIN_TOKEN_KEY + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof LoginUser loginUser && userId.equals(loginUser.getUserId())) {
                    keys.add(key);
                }
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public void removeAll() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(LOGIN_TOKEN_KEY + "*").count(500).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
