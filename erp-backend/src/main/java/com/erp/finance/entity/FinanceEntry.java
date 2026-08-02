package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收付款流水 - 覆盖 6 类:
 * RECEIVABLE 应收(收) / PAYABLE 应付(付) / PRE_RECEIVE 预收定金 / PRE_PAY 预付定金 /
 * UNRECEIVED 应收未收(收) / UNPAID 应付未付(付)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_entry")
public class FinanceEntry extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 流水号 */
    private String entryNo;

    /** 类型 (见类注释 6 类枚举) */
    private String type;

    /** 方向: in 收(进账) / out 付(出账) */
    private String direction;

    /** 客户 id */
    private Long customerId;

    /** 客户业务ID/编号, 如 AU9104 */
    @TableField(exist = false)
    private String customerCode;

    /** 金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 关联单据类型 (如 PI / PO) */
    private String relatedDocType;

    /** 关联单据号 */
    private String relatedDocNo;

    /** 入账日期 */
    private LocalDate entryDate;

    /** 备注 */
    private String remark;
}
