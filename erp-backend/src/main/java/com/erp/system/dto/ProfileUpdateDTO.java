package com.erp.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateDTO {

    @Size(max = 64, message = "姓名不能超过64个字符")
    private String realName;

    @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;

    @Size(max = 64, message = "手机号不能超过64个字符")
    private String phone;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
