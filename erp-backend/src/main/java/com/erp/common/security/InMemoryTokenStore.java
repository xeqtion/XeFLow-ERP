package com.erp.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的登录态存储 (本地开发用, 无需 Redis)。
 * 启用方式: erp.session.store=memory。重启即失效, 不适合多实例。
 */
@Component
@ConditionalOnProperty(name = "erp.session.store", havingValue = "memory")
public class InMemoryTokenStore implements TokenStore {

    private record Entry(LoginUser user, long expireAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public void set(String token, LoginUser loginUser, long ttlMinutes) {
        long expireAt = System.currentTimeMillis() + ttlMinutes * 60_000;
        cache.put(token, new Entry(loginUser, expireAt));
    }

    @Override
    public LoginUser get(String token) {
        Entry entry = cache.get(token);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            cache.remove(token);
            return null;
        }
        return entry.user();
    }

    @Override
    public void remove(String token) {
        cache.remove(token);
    }

    @Override
    public void removeByUserId(Long userId) {
        if (userId != null) {
            cache.forEach((token, entry) -> {
                if (userId.equals(entry.user().getUserId())) {
                    cache.remove(token, entry);
                }
            });
        }
    }

    @Override
    public void removeAll() {
        cache.clear();
    }
}
