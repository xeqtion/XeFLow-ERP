package com.erp.common.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件服务 - 统一发信入口。
 * <p>
 * 当 erp.mail.enabled=false 或未配置 SMTP(无 JavaMailSender)时, 自动降级为
 * 仅打印日志, 使本地开发无需真实邮箱即可走完整的"发码 -> 验证"流程。
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailProperties mailProperties;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, MailProperties mailProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailProperties = mailProperties;
    }

    /** 是否具备真实发信能力 */
    public boolean isRealSendEnabled() {
        return mailProperties.isEnabled() && mailSenderProvider.getIfAvailable() != null;
    }

    /** 邮件未启用时是否允许把验证码回传接口(仅本地联调; 生产为 false) */
    public boolean isExposeDevCode() {
        return mailProperties.isExposeDevCode();
    }

    /** 发送纯文本邮件 */
    public void sendText(String to, String subject, String content) {
        if (!isRealSendEnabled()) {
            log.warn("[MAIL-DISABLED] 收件人={} 主题={} 内容=\n{}", to, subject, content);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getObject();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        sender.send(message);
        log.info("[MAIL] 已发送纯文本邮件 -> {} ({})", to, subject);
    }

    /** 发送 HTML 邮件 (用于报表/通知等富文本) */
    public void sendHtml(String to, String subject, String html) {
        if (!isRealSendEnabled()) {
            log.warn("[MAIL-DISABLED] 收件人={} 主题={} (HTML, 已省略正文)", to, subject);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getObject();
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mime);
            log.info("[MAIL] 已发送 HTML 邮件 -> {} ({})", to, subject);
        } catch (Exception e) {
            log.error("[MAIL] 发送 HTML 邮件失败 -> {}: {}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }
}
