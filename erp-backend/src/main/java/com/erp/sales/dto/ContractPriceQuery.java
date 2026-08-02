package com.erp.sales.dto;

import com.erp.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContractPriceQuery extends PageQuery {

    @Schema(description = "审核状态过滤: PASS/PENDING/REJECTED")
    private String auditStatus;

    @Schema(description = "客户 id 过滤")
    private Long customerId;

    private String customerCode;
}
