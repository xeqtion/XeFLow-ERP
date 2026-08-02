package com.erp.notification.dto;

import com.erp.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 当前用户通知分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知查询")
public class NotificationQuery extends PageQuery {

    @Schema(description = "仅未读")
    private Boolean onlyUnread;

    @Schema(description = "类型过滤 SYSTEM/REPORT/ANNOUNCE")
    private String type;
}
