package com.erp.basedata.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM 明细 导入/导出 Excel 模型 (产品代码 / 物料型号 / 中英名称 / 单价)
 */
@Data
@ColumnWidth(18)
public class BomItemExcel {

    @ExcelProperty("物料型号*")
    private String materialModel;

    @ExcelProperty("中文名")
    private String nameCn;

    @ExcelProperty("英文名")
    private String nameEn;

    @ExcelProperty("数量")
    private BigDecimal qty;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("币种")
    private String currency;
}
