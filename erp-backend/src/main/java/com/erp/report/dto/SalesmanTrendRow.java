package com.erp.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 业务员按季度或年度聚合的业绩趋势。 */
@Data
public class SalesmanTrendRow {
    private String period;
    private Long ownerId;
    private String salesman;
    private Long orderCount;
    private BigDecimal totalAmount;
    private String currency;
}
