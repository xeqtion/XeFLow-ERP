package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "令牌类型")
    private String tokenType = "Bearer";

    public LoginResponse(String token) {
        this.token = token;
        this.tokenType = "Bearer";
    }
}
