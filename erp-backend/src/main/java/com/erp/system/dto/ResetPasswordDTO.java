package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置他人密码
 */
@Data
@Schema(description = "重置密码入参")
public class ResetPasswordDTO {

    @NotNull(message = "缺少用户ID")
    private Long userId;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需 6-32 位")
    private String newPassword;
}
