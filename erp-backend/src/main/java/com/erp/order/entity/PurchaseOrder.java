package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PO 采购订单 主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** PO 单号 */
    private String poNo;

    /** 关联 PI id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long piId;

    /** 关联 PI 单号快照 */
    private String piNo;

    /** 采购人用户 ID */
    private Long purchaserId;

    /** 采购人名称，仅用于展示 */
    @TableField(exist = false)
    private String purchaserName;

    /** 供应商 */
    private String supplier;

    /** 列表展示用采购明细摘要: 产品 x 数量 */
    @TableField(exist = false)
    private String purchaseSummary;

    /** 币种 */
    private String currency;

    /** 合计金额 (由明细汇总) */
    private BigDecimal totalAmount;

    /** 状态: DRAFT 草稿 / CONFIRMED 已确认 / IN_PROGRESS 进行中 / COMPLETED 已完成 */
    private String status;

    /** 下单日期 */
    private LocalDate orderDate;

    /** 备注 */
    private String remark;
}
