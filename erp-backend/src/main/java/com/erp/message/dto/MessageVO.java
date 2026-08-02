package com.erp.message.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图 - 含附件与"是否我发出"标记, 用于会话/收发件箱渲染。
 * id 类字段序列化为字符串, 避免前端(JS Number)精度丢失。
 */
@Data
public class MessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;
    private String senderName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recipientId;
    private String recipientName;
    private String content;
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 是否当前用户发出 */
    private boolean mine;

    private List<AttachmentVO> attachments;

    @Data
    public static class AttachmentVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String fileName;
        private String contentType;
        private Long size;
    }
}
