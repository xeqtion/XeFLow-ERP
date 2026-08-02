package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 - 当前阶段: 注册码闸门 (邮箱验证码可选, 由 erp.register.require-email-code 控制)
 */
@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需 3-32 位")
    @Schema(description = "登录用户名", example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需 6-32 位")
    @Schema(description = "登录密码", example = "Abc123456")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "邮箱验证码(仅当 require-email-code=true 时必填)", example = "123456")
    private String code;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "手机号", example = "13800000000")
    private String phone;

    @Schema(description = "注册码(必填, 不正确无法注册)")
    private String registerCode;
}
