package com.erp.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 周报/月报摘要配置 (前缀 erp.digest)
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.digest")
public class DigestProperties {

    /** 是否启用定时摘要任务 */
    private boolean enabled = true;

    /** 周报 cron, 默认每周一 08:00 */
    private String weeklyCron = "0 0 8 ? * MON";

    /** 月报 cron, 默认每月1日 08:00 */
    private String monthlyCron = "0 0 8 1 * ?";

    /** 摘要是否同时发送邮件 (需 erp.mail.enabled=true 才真正发信) */
    private boolean sendEmail = true;
}
