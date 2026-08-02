package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 产品配件资料。
 *
 * <p>沿用 bom 表和权限路径，业务含义从“BOM 主表”调整为“物料/配件资料”。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bom")
public class Bom extends BaseEntity {

    /** 雪花ID序列化为字符串，避免前端 JS Number 精度丢失。 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 物料编码，自动生成 MT0001 格式。 */
    private String bomCode;

    /** 兼容旧数据：关联产品 id。 */
    private Long productId;

    /** 兼容旧数据：产品代码。 */
    private String prodCode;

    /** 名称。 */
    private String nameCn;

    /** 兼容旧数据：英文名。 */
    private String nameEn;

    /** 兼容旧数据：版本。 */
    private String version;

    /** 支持产品型号，对应产品资料中的公司产品型号；多个用逗号分隔，所有产品通用填“所有”。 */
    private String supportModels;

    /** 是否通用：1 通用，0 非通用。 */
    private Integer universal;

    /** 规格。 */
    private String spec;

    /** 用量。 */
    private BigDecimal qty;

    /** 供应商。 */
    private String supplier;

    /** 联系电话。 */
    private String supplierPhone;

    /** 联系人。 */
    private String supplierContact;

    /** 单价。 */
    private BigDecimal unitPrice;

    /** 币种，默认 CNY。 */
    private String currency;

    /** 备注。 */
    private String remark;

    public BigDecimal getTotalPrice() {
        if (qty == null || unitPrice == null) {
            return null;
        }
        return qty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
    }
}
