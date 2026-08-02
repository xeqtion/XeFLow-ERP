package com.erp.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 - 校验令牌并从 Redis 还原登录态
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final JwtProperties properties;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenService tokenService, JwtProperties properties) {
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtUtil.parse(token);
            if (claims != null) {
                LoginUser loginUser = tokenService.get(token);
                if (loginUser != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(properties.getHeader());
        String prefix = properties.getTokenPrefix();
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length());
        }
        return bearer;
    }
}
