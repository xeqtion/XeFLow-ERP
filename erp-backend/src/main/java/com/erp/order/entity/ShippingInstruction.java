package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 出货指示
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shipping_instruction")
public class ShippingInstruction extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 出货指示单号 */
    private String siNo;

    /** 客户 id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long customerId;

    @TableField(exist = false)
    private String customerCode;

    /** 关联 PI id */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long piId;

    /** 出货日期 */
    private LocalDate shipDate;

    /** 发货物流地址 */
    private String shippingAddress;

    /** 送货地址 */
    private String deliveryAddress;

    /** 联系人 */
    private String contactName;

    /** 电话 */
    private String phone;

    /** 订单编号/PI 单号 */
    private String orderNo;

    /** 订单国家 */
    private String orderCountry;

    /** 产品型号 */
    private String productModel;

    /** 工厂型号 */
    private String factoryModel;

    /** 数量 */
    private java.math.BigDecimal quantity;

    /** 箱数，默认和数量一致 */
    private java.math.BigDecimal cartonCount;

    /** 订单要求 */
    private String orderRequirement;

    /** 负责人/业务员 */
    private String ownerName;

    /** 下单日期 */
    private LocalDate orderDate;

    /** 兼容旧字段: 港口 */
    private String port;

    /** 状态: DRAFT 草稿 / CONFIRMED 已确认 */
    private String status;

    /** 备注 */
    private String remark;

    /** 出货产品明细；旧的主表产品字段继续保留为列表摘要和兼容字段。 */
    @TableField(exist = false)
    private List<ShippingInstructionItem> items;
}
