package com.erp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析工具
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "uid";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成令牌 */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpireMinutes() * 60_000);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 解析令牌, 失败返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    public Long getUserId(Claims claims) {
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    public long getExpireMinutes() {
        return properties.getExpireMinutes();
    }
}
