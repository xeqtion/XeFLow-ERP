package com.erp.shipment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 出货单 (关联出货指示 / PI)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shipment")
public class Shipment extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 出货单号 */
    private String shipmentNo;

    /** 关联出货指示 id */
    private Long siId;

    /** Associated shipping instruction number for display. */
    @TableField(exist = false)
    private String siNo;

    /** 关联 PI id */
    private Long piId;

    /** 出货日期 */
    private LocalDate shipDate;

    /** 数量 */
    private BigDecimal qty;

    /** 箱数/件数 */
    private Integer packageCount;

    /** 毛重(kg) */
    private BigDecimal grossWeight;

    /** 净重(kg) */
    private BigDecimal netWeight;

    /** 体积(CBM) */
    private BigDecimal volumeCbm;

    /** 柜型 (如 20GP / 40HQ) */
    private String containerType;

    /** 柜号 */
    private String containerNo;

    /** 提单号 B/L No. */
    private String blNo;

    /** 状态: DRAFT 草稿 / SHIPPED 已出货 */
    private String status;

    /** 备注 */
    private String remark;
}
