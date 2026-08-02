package com.erp.message.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 联系人 - 可发消息的用户 + 与我之间的未读数
 */
@Data
public class ContactVO {

    /** 序列化为字符串, 避免注册用户(雪花ID)精度丢失致会话/发送目标错位 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 显示名(姓名优先, 否则账号) */
    private String name;

    /** 账号 */
    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    /** 角色文字(便于展示) */
    private String roles;

    /** 该联系人发给我、我尚未读的条数 */
    private long unread;
}
