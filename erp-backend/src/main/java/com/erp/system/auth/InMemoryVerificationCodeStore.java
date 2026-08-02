package com.erp.system.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的验证码存储 (本地开发用, 无需 Redis; erp.session.store=memory)。
 */
@Component
@ConditionalOnProperty(name = "erp.session.store", havingValue = "memory")
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private record Entry(String code, long expireAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public void set(String key, String code, long ttlSeconds) {
        cache.put(key, new Entry(code, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public String get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return entry.code();
    }

    @Override
    public void remove(String key) {
        cache.remove(key);
    }
}
