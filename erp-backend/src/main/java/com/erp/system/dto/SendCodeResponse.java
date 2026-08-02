package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发送验证码结果
 */
@Data
@Schema(description = "发送验证码结果")
public class SendCodeResponse {

    @Schema(description = "是否已通过 SMTP 真实发送")
    private boolean mailSent;

    @Schema(description = "本地降级模式下返回的验证码 (真实发信时为 null), 仅便于开发联调")
    private String devCode;

    public SendCodeResponse(boolean mailSent, String devCode) {
        this.mailSent = mailSent;
        this.devCode = devCode;
    }
}
