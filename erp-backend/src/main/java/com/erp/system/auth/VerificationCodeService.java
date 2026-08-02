package com.erp.system.auth;

import com.erp.common.exception.BusinessException;
import com.erp.common.mail.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 邮箱验证码服务 - 生成、发送、校验。
 * <p>用途(注册/登录)与邮箱组合为存储 key, 互不干扰; 校验通过即失效(一次性)。</p>
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeStore codeStore;
    private final MailService mailService;
    private final RegisterProperties registerProperties;

    public VerificationCodeService(VerificationCodeStore codeStore, MailService mailService,
                                   RegisterProperties registerProperties) {
        this.codeStore = codeStore;
        this.mailService = mailService;
        this.registerProperties = registerProperties;
    }

    /**
     * 生成并发送验证码到指定邮箱。
     *
     * @return 当未配置真实 SMTP(降级日志模式)时返回验证码本身, 便于本地联调; 真实发信时返回 null。
     */
    public String sendCode(String email, CodePurpose purpose) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String key = buildKey(normalized, purpose);
        String cooldownKey = key + ":cd";

        // 冷却: 防止频繁发送
        if (codeStore.get(cooldownKey) != null) {
            throw new BusinessException("验证码发送过于频繁, 请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        int ttlMinutes = registerProperties.getCodeTtlMinutes();
        codeStore.set(key, code, ttlMinutes * 60L);
        codeStore.set(cooldownKey, "1", registerProperties.getCodeCooldownSeconds());

        String purposeLabel = purpose == CodePurpose.REGISTER ? "注册" : "登录";
        String subject = "【XeFlow】" + purposeLabel + "验证码: " + code;
        mailService.sendHtml(normalized, subject, buildHtml(purposeLabel, code, ttlMinutes));

        if (!mailService.isRealSendEnabled()) {
            log.warn("[VCODE] 邮件未启用(降级日志模式), {} 验证码({}) = {}", purposeLabel, normalized, code);
            // 安全: 仅本地联调(erp.mail.expose-dev-code=true)才回传验证码;
            // 生产/试运营返回 null, 验证码不经接口泄露(请务必配置 SMTP, 否则用户收不到码)。
            return mailService.isExposeDevCode() ? code : null;
        }
        return null;
    }

    /** 校验验证码; 通过则使其失效。失败抛业务异常。 */
    public void verify(String email, CodePurpose purpose, String inputCode) {
        if (!StringUtils.hasText(inputCode)) {
            throw new BusinessException("请输入验证码");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String key = buildKey(normalized, purpose);
        String saved = codeStore.get(key);
        if (saved == null) {
            throw new BusinessException("验证码不存在或已过期, 请重新获取");
        }
        if (!saved.equals(inputCode.trim())) {
            throw new BusinessException("验证码错误");
        }
        codeStore.remove(key);
    }

    /** 验证码 HTML 邮件模板 (邮件客户端兼容: 内联样式 + 表格布局) */
    private String buildHtml(String purposeLabel, String code, int ttlMinutes) {
        return "<div style=\"max-width:480px;margin:0 auto;font-family:'Microsoft YaHei',Arial,sans-serif;"
                + "border:1px solid #ebeef5;border-radius:8px;overflow:hidden\">"
                + "<div style=\"background:#1677ff;color:#fff;padding:16px 24px;font-size:18px;font-weight:bold\">XeFlow</div>"
                + "<div style=\"padding:24px\">"
                + "<p style=\"color:#303133;font-size:15px;margin:0 0 12px\">您正在进行 <b>" + purposeLabel + "</b> 操作，验证码如下：</p>"
                + "<div style=\"text-align:center;margin:20px 0\">"
                + "<span style=\"display:inline-block;font-size:30px;letter-spacing:8px;font-weight:bold;color:#409eff;"
                + "background:#ecf5ff;padding:12px 24px;border-radius:6px\">" + code + "</span></div>"
                + "<p style=\"color:#909399;font-size:13px;margin:12px 0 0\">该验证码 " + ttlMinutes + " 分钟内有效，请勿泄露给他人。</p>"
                + "<p style=\"color:#909399;font-size:13px;margin:6px 0 0\">若非本人操作，请忽略本邮件。</p>"
                + "</div></div>";
    }

    private String buildKey(String email, CodePurpose purpose) {
        return purpose.name().toLowerCase(Locale.ROOT) + ":" + email;
    }
}
