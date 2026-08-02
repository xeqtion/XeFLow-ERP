package com.erp.basedata.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 产品配件资料导入/导出模型。
 *
 * <p>数量和金额使用文本承接，确保格式错误可以精确反馈到单行，
 * 不会让整份 Excel 解析失败。</p>
 */
@Data
@ColumnWidth(18)
public class BomExcel {

    @ExcelProperty("物料编码（选填）")
    private String bomCode;

    @ExcelProperty("名称")
    private String nameCn;

    @ExcelProperty("是否通用")
    private String universal;

    @ExcelProperty("支持产品型号")
    private String supportModels;

    @ExcelProperty("规格")
    private String spec;

    @ExcelProperty("用量")
    private String qty;

    @ExcelProperty("供应商")
    private String supplier;

    @ExcelProperty("联系电话")
    private String supplierPhone;

    @ExcelProperty("联系人")
    private String supplierContact;

    @ExcelProperty("单价")
    private String unitPrice;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("备注")
    private String remark;
}
