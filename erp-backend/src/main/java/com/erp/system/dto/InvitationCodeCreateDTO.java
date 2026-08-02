package com.erp.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationCodeCreateDTO {

    @Future(message = "邀请码有效期必须晚于当前时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @NotNull(message = "请填写可用次数")
    @Min(value = 1, message = "可用次数不能少于 1 次")
    @Max(value = 10000, message = "可用次数不能超过 10000 次")
    private Integer maxUses;
}
