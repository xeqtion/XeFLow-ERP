package com.erp.finance.dto;

import com.erp.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收账款查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReceivableQuery extends PageQuery {

    /** 按客户过滤 */
    private Long customerId;

    /** 按客户业务ID过滤, 如 AU9104 */
    private String customerCode;

    /** 结算状态: UNPAID 未收 / PARTIAL 部分 / PAID 已结清 */
    private String status;

    /** 仅看逾期 */
    private Boolean overdueOnly;

    /** 账期天数(超过即逾期), 默认 30 */
    private Integer termDays;
}
