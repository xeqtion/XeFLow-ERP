package com.erp.report.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 业务员业绩来源 PI 明细。 */
@Data
public class SalesPerformanceDetail {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long piId;
    private String piNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    private String salesman;
    private String customerCode;
    private String customerName;
    private LocalDate signDate;
    private String status;
    private String currency;
    private BigDecimal originalAmount;
    private BigDecimal amountCny;
}
