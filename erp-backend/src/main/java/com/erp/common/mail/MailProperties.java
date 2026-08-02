package com.erp.common.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件发送配置 (前缀 erp.mail)。
 * SMTP 连接参数复用 Spring 原生 spring.mail.*; 这里仅控制开关与发件人。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.mail")
public class MailProperties {

    /** 是否真正通过 SMTP 发信; false 时仅打印到日志(本地无邮箱也能测试验证码) */
    private boolean enabled = false;

    /** Sender, for example "XeFlow <noreply@example.com>". */
    private String from = "XeFlow <noreply@example.com>";

    /**
     * 邮件未启用时, 是否把验证码直接回传到接口响应(仅供本地联调)。
     * <p>生产/试运营务必为 false: 否则未配置 SMTP 时验证码会经接口泄露, 邮箱归属校验形同虚设。</p>
     */
    private boolean exposeDevCode = false;
}
