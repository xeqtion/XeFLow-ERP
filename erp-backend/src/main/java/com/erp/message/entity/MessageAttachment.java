package com.erp.message.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息附件 - 隶属某条 user_message
 */
@Data
@TableName("message_attachment")
public class MessageAttachment implements Serializable {

    @TableId
    private Long id;

    /** 所属消息ID */
    private Long messageId;

    /** 原始文件名 */
    private String fileName;

    /** 磁盘存储名 */
    private String storedName;

    /** MIME 类型 */
    private String contentType;

    /** 字节大小 */
    private Long size;
}
