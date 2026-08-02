package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送邮箱验证码请求 (注册 / 验证码登录共用)
 */
@Data
@Schema(description = "发送邮箱验证码请求")
public class SendCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "目标邮箱", example = "user@example.com")
    private String email;
}
