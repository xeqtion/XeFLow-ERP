package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 注册选项 - 供前端按当前注册策略渲染注册表单 (公开接口)。
 */
@Data
@Schema(description = "注册选项")
public class RegisterOptionsVO {

    @Schema(description = "是否开放注册 (开关开启且已配置注册码)")
    private boolean enabled;

    @Schema(description = "是否需要注册码")
    private boolean requireRegisterCode;

    @Schema(description = "是否需要邮箱验证码")
    private boolean requireEmailCode;

    @Schema(description = "允许注册的邮箱域名白名单 (为空表示不限制域名)")
    private List<String> allowedDomains;
}
