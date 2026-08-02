package com.erp.common.security;

import org.springframework.stereotype.Service;

/**
 * 令牌会话服务 - 登录态读写的统一入口, 底层由 {@link TokenStore} 策略实现
 * (默认 Redis, 本地开发可切内存)。
 */
@Service
public class TokenService {

    private final TokenStore tokenStore;
    private final JwtUtil jwtUtil;

    public TokenService(TokenStore tokenStore, JwtUtil jwtUtil) {
        this.tokenStore = tokenStore;
        this.jwtUtil = jwtUtil;
    }

    public void cache(String token, LoginUser loginUser) {
        tokenStore.set(token, loginUser, jwtUtil.getExpireMinutes());
    }

    public LoginUser get(String token) {
        return tokenStore.get(token);
    }

    public void remove(String token) {
        tokenStore.remove(token);
    }

    /** 使指定用户在所有终端上的登录令牌失效。 */
    public void invalidateUser(Long userId) {
        if (userId != null) {
            tokenStore.removeByUserId(userId);
        }
    }

    /** Force every client to authenticate again after a system-wide reset. */
    public void invalidateAll() {
        tokenStore.removeAll();
    }
}
