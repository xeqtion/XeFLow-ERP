package com.erp.basedata.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 客户资料 导入/导出 Excel 模型
 */
@Data
@ColumnWidth(20)
public class CustomerExcel {

    @ExcelProperty("客户ID")
    private String custCode;

    @ExcelProperty("联系人姓名")
    private String contactName;

    @ExcelProperty("职位")
    private String contactPosition;

    @ExcelProperty("国家")
    private String country;

    @ExcelProperty("公司名称")
    private String companyName;

    @ExcelProperty("地址")
    private String address;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("网站")
    private String website;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("电子邮箱")
    private String email;

    @ExcelProperty("社媒联系")
    private String socialContact;

    @ExcelProperty("备注")
    private String remark;
}
