package com.erp.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商应付账龄。每行只统计一个单据原币。
 */
@Data
public class SupplierAging {

    private String supplier;
    private String currency;
    private BigDecimal d0_30 = BigDecimal.ZERO;
    private BigDecimal d31_60 = BigDecimal.ZERO;
    private BigDecimal d61_90 = BigDecimal.ZERO;
    private BigDecimal d90plus = BigDecimal.ZERO;
    private BigDecimal totalBalance = BigDecimal.ZERO;
}
