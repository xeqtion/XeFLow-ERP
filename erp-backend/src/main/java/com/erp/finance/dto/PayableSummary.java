package com.erp.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付账款汇总。每个对象只汇总一个单据原币。
 */
@Data
public class PayableSummary {

    private String currency;
    private BigDecimal totalPayable = BigDecimal.ZERO;
    private BigDecimal totalPaid = BigDecimal.ZERO;
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal overdueBalance = BigDecimal.ZERO;
    private long poCount;
    private long openCount;
}
