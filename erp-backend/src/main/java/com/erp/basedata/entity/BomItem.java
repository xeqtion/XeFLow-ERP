package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 产品清单 BOM 明细 (物料行)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bom_item")
public class BomItem extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属 BOM id */
    private Long bomId;

    /** 物料型号 */
    private String materialModel;

    /** 中文名 */
    private String nameCn;

    /** 英文名 */
    private String nameEn;

    /** 数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 币种 */
    private String currency;
}
