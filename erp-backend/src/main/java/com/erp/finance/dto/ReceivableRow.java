package com.erp.finance.dto;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收账款行 - 以 PI 为单位的应收/已收/未收/账龄。
 */
@Data
public class ReceivableRow {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long piId;
    private String piNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String currency;
    private LocalDate signDate;

    /** 应收(PI 合计) */
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private BigDecimal depositReceivedAmount;
    /** 已收(关联本 PI 单号的收款合计) */
    private BigDecimal receivedAmount;
    /** 未收余额 */
    private BigDecimal balance;
    /** 以下金额用于汇总与账龄，均按单据/入账日期折算为本位币。 */
    private String baseCurrency;
    private BigDecimal totalAmountBase;
    private BigDecimal receivedAmountBase;
    private BigDecimal balanceBase;
    /** 结算状态: UNPAID / PARTIAL / PAID */
    private String settleStatus;
    /** 账龄天数(自签约日) */
    private Integer ageDays;
    /** 是否逾期(有余额且账龄超账期) */
    private Boolean overdue;
}
