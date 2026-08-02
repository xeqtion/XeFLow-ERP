package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出货指示产品明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shipping_instruction_item")
public class ShippingInstructionItem extends BaseEntity {

    /** 雪花ID序列化为字符串，避免前端 JavaScript 精度丢失。 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属出货指示。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long siId;

    /** 产品型号。 */
    private String productModel;

    /** 工厂型号。 */
    private String factoryModel;

    /** 数量。 */
    private BigDecimal quantity;

    /** 箱数。 */
    private BigDecimal cartonCount;

    /** 表单及导出顺序。 */
    private Integer sortOrder;
}
