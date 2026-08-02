package com.erp.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按业务员(担当)汇总行
 */
@Data
public class SalesmanRow {
    private Long ownerId;
    private String salesman;
    private Long orderCount;
    private BigDecimal totalAmount;
    /** 占统计区间总营收的百分比。 */
    private BigDecimal revenueShare;
    private String currency;
}
