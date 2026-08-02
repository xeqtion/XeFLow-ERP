package com.erp.sales.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品报价导入/导出 Excel 模型。
 */
@Data
@ColumnWidth(18)
public class StandardPriceExcel {

    @ExcelProperty("产品ID*")
    private Long productId;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("公司产品型号")
    private String companyModel;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("标准单价*")
    private BigDecimal standardUnitPrice;

    @ExcelProperty("折扣单价*")
    private BigDecimal discountUnitPrice;

    @ExcelProperty("底价")
    private BigDecimal floorPrice;

    @ExcelProperty("币种")
    private String currency;
}
