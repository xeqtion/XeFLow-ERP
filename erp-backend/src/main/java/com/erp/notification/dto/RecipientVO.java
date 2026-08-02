package com.erp.notification.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 公告可选接收人 - 用于管理员发送公告时挑选用户
 */
@Data
public class RecipientVO {

    /** 用户ID(字符串化, 防雪花精度丢失) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 显示名(姓名优先, 否则账号) */
    private String name;

    public RecipientVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
