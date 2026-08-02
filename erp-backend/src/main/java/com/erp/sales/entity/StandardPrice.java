package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 产品报价。
 *
 * <p>保留 price / prodCode / customerId / effectiveDate 等旧字段用于既有销售报价比对和历史数据兼容；
 * 新页面以产品资料字段和报价字段为主。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("standard_price")
public class StandardPrice extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 产品 id */
    private Long productId;

    /** 产品代码 */
    private String prodCode;

    /** 产品名称 */
    private String productName;

    /** 公司产品型号 */
    private String companyModel;

    /** 客户 id (可空, 空为通用报价) */
    private Long customerId;

    /** 兼容旧字段: 系统单价/标准价 */
    private BigDecimal price;

    /** 标准单价 */
    private BigDecimal standardUnitPrice;

    /** 折扣单价 */
    private BigDecimal discountUnitPrice;

    /** 底价, 默认等于折扣单价 */
    private BigDecimal floorPrice;

    /** 币种 */
    private String currency;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 备注 */
    private String remark;

    public BigDecimal getStandardUnitPrice() {
        return standardUnitPrice != null ? standardUnitPrice : price;
    }

    public BigDecimal getFloorPrice() {
        return floorPrice != null ? floorPrice : discountUnitPrice;
    }
}
