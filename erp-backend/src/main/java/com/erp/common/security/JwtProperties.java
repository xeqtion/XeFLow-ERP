package com.erp.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性 (前缀 erp.jwt)
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.jwt")
public class JwtProperties {

    /** 签名密钥 */
    private String secret;

    /** 访问令牌有效期(分钟) */
    private long expireMinutes = 720;

    /** 请求头名称 */
    private String header = "Authorization";

    /** 令牌前缀 */
    private String tokenPrefix = "Bearer ";
}
