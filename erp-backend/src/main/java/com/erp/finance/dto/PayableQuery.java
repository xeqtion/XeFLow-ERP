package com.erp.finance.dto;

import com.erp.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应付账款查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayableQuery extends PageQuery {

    /** 按供应商过滤(模糊) */
    private String supplier;

    /** 结算状态: UNPAID / PARTIAL / PAID */
    private String status;

    /** 仅看逾期 */
    private Boolean overdueOnly;

    /** 账期天数, 默认 30 */
    private Integer termDays;
}
