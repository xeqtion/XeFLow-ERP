package com.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单序列报表行 (采购/销售)
 */
@Data
public class ReportRow {

    @ExcelProperty("周期")
    private String period;

    @ExcelProperty("金额(CNY)")
    private BigDecimal amount;

    private String currency;
}
