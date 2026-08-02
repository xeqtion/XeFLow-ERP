package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 销售合同 PI (形式发票) 主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_pi")
public class SalesPi extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 归属业务员(担当)用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long ownerId;

    /** PI 单号 */
    private String piNo;

    /** 客户内部 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户业务ID/编号, 如 US0001 */
    @TableField(exist = false)
    private String customerCode;

    /** 客户名称 */
    @TableField(exist = false)
    private String customerName;

    /** 币种 */
    private String currency;

    /** 合计金额 (由明细汇总) */
    private BigDecimal totalAmount;

    /** Contract deposit amount. */
    private BigDecimal depositAmount;

    /** 状态: DRAFT 草稿 / CONFIRMED 已确认 / ORDERED 已下单 / SHIPPED 已出货 / COMPLETED 已完成 */
    private String status;

    /** 签约日期 */
    private LocalDate signDate;

    /** 付款方式 (如 T/T, L/C at sight, 30% deposit) */
    private String paymentMethod;

    /** 贸易条款 Incoterms (如 FOB Shenzhen, CIF Rotterdam) */
    private String tradeTerm;

    /** 交货期 */
    private LocalDate deliveryDate;

    /** 目的港 */
    private String destPort;

    /** 唛头 Shipping Marks */
    private String shippingMarks;

    /** 备注 */
    private String remark;

    /** 已实际出货总数量，仅用于列表与详情展示。 */
    @TableField(exist = false)
    private BigDecimal shippedQty;

    /** 尚未出货总数量，仅用于列表与详情展示。 */
    @TableField(exist = false)
    private BigDecimal remainingShipQty;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getDraftExpireTime() {
        return "DRAFT".equals(status) && getCreateTime() != null ? getCreateTime().plusHours(2) : null;
    }

    public Long getDraftRemainingSeconds() {
        LocalDateTime expireTime = getDraftExpireTime();
        if (expireTime == null) {
            return null;
        }
        return Math.max(Duration.between(LocalDateTime.now(), expireTime).getSeconds(), 0);
    }
}
