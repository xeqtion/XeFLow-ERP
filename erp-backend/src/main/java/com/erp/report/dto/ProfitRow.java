package com.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 利润报表行: 销售 - 采购 = 利润
 */
@Data
public class ProfitRow {

    @ExcelProperty("周期")
    private String period;

    @ExcelProperty("销售额(CNY)")
    private BigDecimal sales;

    @ExcelProperty("采购额(CNY)")
    private BigDecimal purchase;

    @ExcelProperty("利润(CNY)")
    private BigDecimal profit;

    private String currency;
}
