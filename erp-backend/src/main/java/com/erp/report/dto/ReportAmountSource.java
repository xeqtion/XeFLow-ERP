package com.erp.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single business amount before conversion to the accounting currency. */
@Data
public class ReportAmountSource {
    private LocalDate businessDate;
    private String currency;
    private BigDecimal amount;
    private Long ownerId;
    private String salesman;
}
