package com.erp.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知 - 每条记录对应一个接收用户 (广播即每个用户一行)
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    /** 雪花ID过长, 序列化为字符串避免前端(JS Number)精度丢失导致按 id 删除/标记失效 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 接收用户ID */
    private Long recipientId;

    private String title;

    /** 正文(可含简单 HTML) */
    private String content;

    /** 类型: SYSTEM/REPORT/ANNOUNCE */
    private String type;

    /** 公告分组键(同一次广播的所有副本共享), 便于管理员整条删除 */
    private String groupKey;

    /** 0 未读 1 已读 */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
