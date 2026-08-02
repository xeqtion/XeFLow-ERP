package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售合同 PI 明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_pi_item")
public class SalesPiItem extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属 PI id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long piId;

    /** 产品 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 产品代码 */
    private String prodCode;

    /** 产品名称快照 */
    private String productName;

    /** 产品型号快照 (优先公司产品型号) */
    private String productModel;

    /** 工厂产品型号快照 */
    private String factoryModel;

    /** 单位 */
    private String unit;

    /** 关联销售报价 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long quoteId;

    /** 报价来源: SALES_QUOTE/STANDARD_PRICE/DISCOUNT_PRICE/FLOOR_PRICE, 空为手动 */
    private String quoteSource;

    /** 产品报价标准单价快照 */
    private BigDecimal standardPrice;

    /** 产品报价折扣单价快照 */
    private BigDecimal discountUnitPrice;

    /** 产品报价底价快照 */
    private BigDecimal floorPrice;

    /** 数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 = 数量 * 单价 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
