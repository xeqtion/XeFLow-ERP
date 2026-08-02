package com.erp.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收账款汇总。每个对象只汇总一个单据原币，禁止跨币种直接相加。
 */
@Data
public class ReceivableSummary {

    /** 单据原币 */
    private String currency;
    /** 应收合计 */
    private BigDecimal totalReceivable = BigDecimal.ZERO;
    /** 已收合计 */
    private BigDecimal totalReceived = BigDecimal.ZERO;
    /** 未收合计 */
    private BigDecimal totalBalance = BigDecimal.ZERO;
    /** 逾期未收合计 */
    private BigDecimal overdueBalance = BigDecimal.ZERO;
    /** PI 张数 */
    private long piCount;
    /** 未结清(有余额) PI 张数 */
    private long openCount;
}
