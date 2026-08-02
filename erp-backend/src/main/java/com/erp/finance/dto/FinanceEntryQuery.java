package com.erp.finance.dto;

import com.erp.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceEntryQuery extends PageQuery {

    @Schema(description = "类型过滤")
    private String type;

    @Schema(description = "客户 id 过滤")
    private Long customerId;

    @Schema(description = "客户业务ID过滤, 如 AU9104")
    private String customerCode;

    @Schema(description = "入账日期起")
    private LocalDate startDate;

    @Schema(description = "入账日期止")
    private LocalDate endDate;
}
