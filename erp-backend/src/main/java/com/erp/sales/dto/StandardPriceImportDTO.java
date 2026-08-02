package com.erp.sales.dto;

import java.math.BigDecimal;

/**
 * 产品报价导入 DTO —— 与 Entity 解耦，支持别名导入的字段集合。
 */
public class StandardPriceImportDTO {

    /** 工厂产品型号（用于按此查找已有 Product） */
    private String factoryModel;

    /** 产品名称 */
    private String productName;

    /** 公司产品型号 */
    private String companyModel;

    /** 备注 */
    private String remark;

    /** 出厂价/标准单价 */
    private BigDecimal factoryPrice;

    /** 币种 */
    private String currency;

    public String getFactoryModel() { return factoryModel; }
    public void setFactoryModel(String factoryModel) { this.factoryModel = factoryModel; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCompanyModel() { return companyModel; }
    public void setCompanyModel(String companyModel) { this.companyModel = companyModel; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public BigDecimal getFactoryPrice() { return factoryPrice; }
    public void setFactoryPrice(BigDecimal factoryPrice) { this.factoryPrice = factoryPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
