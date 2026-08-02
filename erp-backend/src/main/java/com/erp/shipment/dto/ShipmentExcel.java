package com.erp.shipment.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 出货单 导入/导出 Excel 模型
 */
@Data
@ColumnWidth(18)
public class ShipmentExcel {

    @ExcelProperty("出货单号*")
    private String shipmentNo;

    @ExcelProperty("关联出货指示ID")
    private Long siId;

    @ExcelProperty("关联PI_ID")
    private Long piId;

    @ExcelProperty("出货日期")
    private LocalDate shipDate;

    @ExcelProperty("数量")
    private BigDecimal qty;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;
}
