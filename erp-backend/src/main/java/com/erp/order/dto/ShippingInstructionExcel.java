package com.erp.order.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDate;

/**
 * 出货指示 导入/导出 Excel 模型
 */
@Data
@ColumnWidth(18)
public class ShippingInstructionExcel {

    @ExcelProperty("出货指示单号*")
    private String siNo;

    @ExcelProperty("客户ID")
    private String customerCode;

    @ExcelProperty("关联PI_ID")
    private Long piId;

    @ExcelProperty("出货日期")
    private LocalDate shipDate;

    @ExcelProperty("港口")
    private String port;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;
}
