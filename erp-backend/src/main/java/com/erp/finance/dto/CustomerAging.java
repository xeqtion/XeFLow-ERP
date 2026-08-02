package com.erp.finance.dto;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 客户应收账龄。每行只统计一个单据原币。
 */
@Data
public class CustomerAging {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String currency;
    private BigDecimal d0_30 = BigDecimal.ZERO;
    private BigDecimal d31_60 = BigDecimal.ZERO;
    private BigDecimal d61_90 = BigDecimal.ZERO;
    private BigDecimal d90plus = BigDecimal.ZERO;
    /** 未收合计(单据原币) */
    private BigDecimal totalBalance = BigDecimal.ZERO;
}
