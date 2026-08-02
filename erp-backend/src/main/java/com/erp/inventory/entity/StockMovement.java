package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存出入库流水。结存 = 同 prod_code 的 IN 合计 - OUT 合计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stock_movement")
public class StockMovement extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 产品代码 */
    private String prodCode;

    /** 产品 id(可空) */
    private Long productId;

    /** 产品名称快照，支持手填产品代码的库存展示。 */
    private String productName;

    /** 方向: IN 入库 / OUT 出库 */
    private String direction;

    /** 数量 */
    private BigDecimal qty;

    /** 业务类型: PURCHASE_IN 采购入库 / SALE_OUT 销售出库 / BOM_OUT 生产领料 / ADJUST 盘点调整 / MANUAL 手工 */
    private String bizType;

    /** 关联单据号(如 PO/出货单) */
    private String relatedDocNo;

    /** 业务日期 */
    private LocalDate moveDate;

    /** 备注 */
    private String remark;
}
