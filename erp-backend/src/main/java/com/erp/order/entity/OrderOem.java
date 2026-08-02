package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户 OEM 要求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_oem")
public class OrderOem extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 归属业务员(担当)用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long ownerId;

    /** OEM 单号 */
    private String oemNo;

    /** 关联 PI id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long piId;

    /** 关联 PI 单号快照 */
    private String piNo;

    /** 客户 id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long customerId;

    /** 客户业务ID/编号快照, 如 AU9104 */
    private String customerCode;

    /** 客户名称快照 */
    private String customerName;

    /** 产品 id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long productId;

    /** 产品型号快照 */
    private String productModel;

    /** 工厂型号快照 */
    private String factoryModel;

    /** OEM 要求 */
    private String requirement;

    /** 状态: DRAFT 草稿 / CONFIRMED 已确认 */
    private String status;
}
