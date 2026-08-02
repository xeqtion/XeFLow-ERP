package com.erp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemResetRequest {

    private Boolean preserveUsers = true;

    @NotBlank(message = "请输入超级管理员密码")
    private String password;
}
