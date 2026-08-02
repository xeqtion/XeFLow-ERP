package com.erp.system.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的验证码存储 (默认; 与登录态共用 erp.session.store=redis)。
 */
@Component
@ConditionalOnProperty(name = "erp.session.store", havingValue = "redis", matchIfMissing = true)
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String KEY_PREFIX = "erp:vcode:";

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String key, String code, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + key, code, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + key);
    }

    @Override
    public void remove(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
