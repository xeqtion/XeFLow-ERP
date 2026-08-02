package com.erp.finance.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付账款行 - 以 PO 为单位的应付/已付/未付/账龄。
 */
@Data
public class PayableRow {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long poId;
    private String poNo;
    private String supplier;
    private String currency;
    private String baseCurrency;
    private LocalDate orderDate;

    /** 应付(PO 合计) */
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    /** 已付(关联本 PO 单号的付款合计) */
    private BigDecimal paidAmount;
    private BigDecimal paidAmountBase;
    /** 未付余额 */
    private BigDecimal balance;
    private BigDecimal balanceBase;
    /** 结算状态: UNPAID / PARTIAL / PAID */
    private String settleStatus;
    /** 账龄天数(自下单日) */
    private Integer ageDays;
    /** 是否逾期 */
    private Boolean overdue;
}
