package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Administrator-created invitation code used for account registration. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_invitation_code")
public class InvitationCode extends BaseEntity {

    private String inviteCode;

    private LocalDateTime expireTime;

    private Integer maxUses;

    private Integer usedCount;
}
