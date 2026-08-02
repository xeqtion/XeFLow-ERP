package com.erp.basedata.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品资料 导入/导出 Excel 模型
 */
@Data
@ColumnWidth(18)
public class ProductExcel {

    /** 外部文件中的参考ID；导入时不会直接作为数据库主键。 */
    @ExcelProperty("产品ID")
    private String id;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("公司产品型号")
    private String companyModel;

    @ExcelProperty("工厂产品型号")
    private String factoryModel;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("出厂价")
    private BigDecimal factoryPrice;

    @ExcelProperty("币种")
    private String currency;
}
