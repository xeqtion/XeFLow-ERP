package com.erp.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 管理员发送公告 (可指定接收人, 留空=全部启用用户)
 */
@Data
@Schema(description = "发送公告入参")
public class BroadcastRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @Schema(description = "是否同时发送邮件")
    private boolean sendEmail = false;

    @Schema(description = "指定接收人ID列表; 为空表示推送给全部启用用户")
    private List<Long> recipientIds;
}
