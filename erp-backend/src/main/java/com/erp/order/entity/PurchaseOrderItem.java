package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * PO 采购订单 明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order_item")
public class PurchaseOrderItem extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属 PO id */
    private Long poId;

    /** 产品 id */
    private Long productId;

    /** 产品代码 */
    private String prodCode;

    /** 产品名称 */
    private String productName;

    /** 数量 */
    private BigDecimal qty;

    /** 累计到货入库数量 */
    private BigDecimal receivedQty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 = 数量 * 单价 */
    private BigDecimal amount;
}
