package com.erp.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationCodeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String inviteCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    private Integer maxUses;

    private Integer usedCount;

    private Integer remainingUses;

    /** VALID / EXPIRED / EXHAUSTED; null expireTime means permanent. */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
