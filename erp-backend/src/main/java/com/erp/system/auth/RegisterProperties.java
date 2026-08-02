package com.erp.system.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 注册限制配置 (前缀 erp.register)。
 * <p>当前阶段: 以"注册码"为主闸门, 暂不限制邮箱域名; 后续可开启
 * {@code allowedDomains}(公司邮箱白名单) + {@code requireEmailCode}(邮箱验证码) 切回公司邮箱注册。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.register")
public class RegisterProperties {

    /** 是否开放注册 */
    private boolean enabled = true;

    /** 允许注册的邮箱域名(小写); 为空表示不限制域名 (当前阶段留空) */
    private List<String> allowedDomains = new ArrayList<>();

    /** 注册码; 为空表示注册未开放, 非空时注册必须携带且匹配 (当前阶段的主闸门) */
    private String registerCode;

    /** 是否要求邮箱验证码 (证明邮箱归属); false 时注册无需邮箱验证码 (当前阶段关闭) */
    private boolean requireEmailCode = false;

    /** 新用户默认角色 role_key */
    private String defaultRole = "readonly";

    /** 新用户是否需管理员审核启用 (true 时注册后 status=0) */
    private boolean requireApproval = false;

    /** 验证码有效期(分钟) */
    private int codeTtlMinutes = 10;

    /** 同一邮箱重复发送验证码的冷却(秒) */
    private int codeCooldownSeconds = 60;
}
