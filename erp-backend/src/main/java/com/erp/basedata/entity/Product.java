package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 产品资料。
 *
 * <p>{@code id} 是系统产品ID/主键, 自动生成数字。
 * 新字段用于产品资料主界面; 旧字段保留给订单、库存、销售报价等既有模块兼容。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 旧产品代码/编号, 兼容订单、库存等模块 */
    private String prodCode;

    /** 产品名称 */
    private String productName;

    /** 公司产品型号 */
    private String companyModel;

    /** 工厂产品型号 */
    private String factoryModel;

    /** 出厂价 */
    private BigDecimal factoryPrice;

    /** 旧中文名 */
    private String prodNameCn;

    /** 旧英文名 */
    private String prodNameEn;

    /** 旧规格/型号 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 生产成本价 */
    private BigDecimal costPrice;

    /** 币种 */
    private String currency;

    /** 安全库存(低于则预警) */
    private BigDecimal safetyStock;

    /** 备注 */
    private String remark;
}
