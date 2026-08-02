package com.erp.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户间消息 - 一发一收(类似邮件/企业微信单聊), 可携带附件。
 * 双方各自可删除自己一侧的会话, 互不影响。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_message")
public class UserMessage extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 发件人ID */
    private Long senderId;

    /** 发件人姓名(冗余) */
    private String senderName;

    /** 收件人ID */
    private Long recipientId;

    /** 收件人姓名(冗余) */
    private String recipientName;

    /** 正文 */
    private String content;

    /** 收件人是否已读: 0 未读 1 已读 */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    /** 发件人是否已从自己一侧删除 */
    private Integer senderDeleted;

    /** 收件人是否已从自己一侧删除 */
    private Integer recipientDeleted;
}
