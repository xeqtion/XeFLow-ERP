package com.erp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeactivateAccountDTO {

    @NotBlank(message = "请输入当前密码")
    private String password;
}
