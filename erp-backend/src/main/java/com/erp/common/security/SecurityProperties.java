package com.erp.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置 (前缀 erp.security): CORS 白名单 + 登录失败锁定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.security")
public class SecurityProperties {

    private Cors cors = new Cors();
    private Login login = new Login();

    @Data
    public static class Cors {
        /** 允许的前端源(精确或带通配的 origin pattern); 默认仅本地开发 */
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173", "http://127.0.0.1:5173"));
    }

    @Data
    public static class Login {
        /** 锁定前允许的连续失败次数 */
        private int maxAttempts = 5;
        /** 锁定时长(分钟) */
        private int lockMinutes = 15;
    }
}
