package com.erp.common.audit;

import com.erp.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计日志分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审计日志查询")
public class AuditLogQuery extends PageQuery {

    @Schema(description = "操作人用户名(模糊)")
    private String username;

    @Schema(description = "模块(模糊)")
    private String module;

    @Schema(description = "是否成功 1成功 0失败, 空=全部")
    private Integer success;

    @Schema(description = "起始日期 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;
}
