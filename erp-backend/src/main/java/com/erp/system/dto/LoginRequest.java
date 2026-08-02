package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录请求 - 支持两种方式:
 * <ul>
 *   <li>loginType=password: 用户名 + 密码</li>
 *   <li>loginType=code: 邮箱 + 邮箱验证码</li>
 * </ul>
 * 字段按类型在服务层校验, 故此处不强制 @NotBlank。
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "登录方式: password 用户名密码 / code 邮箱验证码", example = "password")
    private String loginType = "password";

    @Schema(description = "用户名(密码登录)", example = "admin")
    private String username;

    @Schema(description = "密码(密码登录)", example = "Use-A-Strong-Password1!")
    private String password;

    @Schema(description = "邮箱(验证码登录)", example = "user@example.com")
    private String email;

    @Schema(description = "邮箱验证码(验证码登录)", example = "123456")
    private String code;
}
