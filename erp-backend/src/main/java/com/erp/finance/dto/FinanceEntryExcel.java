package com.erp.finance.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收付款流水 导入/导出 Excel 模型
 */
@Data
@ColumnWidth(18)
public class FinanceEntryExcel {

    @ExcelProperty("流水号")
    private String entryNo;

    @ExcelProperty("类型*(RECEIVABLE/PAYABLE/PRE_RECEIVE/PRE_PAY/UNRECEIVED/UNPAID)")
    private String type;

    @ExcelProperty("方向(in收/out付,可空自动)")
    private String direction;

    @ExcelProperty("客户ID")
    private String customerCode;

    @ExcelProperty("金额*")
    private BigDecimal amount;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("关联单据类型")
    private String relatedDocType;

    @ExcelProperty("关联单据号")
    private String relatedDocNo;

    @ExcelProperty("入账日期")
    private LocalDate entryDate;

    @ExcelProperty("备注")
    private String remark;
}
