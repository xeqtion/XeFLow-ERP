package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 销售报价 (含产品报价区间比对结果 + 审核状态)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_price")
public class ContractPrice extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 创建人ID序列化为字符串, 供前端判断本人操作权限 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getCreateBy() {
        return super.getCreateBy();
    }

    /** 更新人ID序列化为字符串 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getUpdateBy() {
        return super.getUpdateBy();
    }

    /** 客户内部 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户业务ID/编号, 如 US0001 */
    @TableField(exist = false)
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 产品 id */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 产品型号 */
    private String productModel;

    /** 产品代码 */
    private String prodCode;

    /** 比对时取到的产品报价标准单价 */
    private BigDecimal standardPrice;

    /** 成交单价 (旧字段名 contractPrice 继续兼容既有数据/API) */
    private BigDecimal contractPrice;

    /** 偏差率 (基于标准价) */
    private BigDecimal deviation;

    /** 币种 */
    private String currency;

    /** 审核状态: PASS 通过 / PENDING 待审核 / REJECTED 驳回 */
    private String auditStatus;

    /** 审核人 id */
    private Long auditorId;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    public BigDecimal getDealUnitPrice() {
        return contractPrice;
    }

    public void setDealUnitPrice(BigDecimal dealUnitPrice) {
        this.contractPrice = dealUnitPrice;
    }

    public BigDecimal getUnitPriceDeviation() {
        return deviation;
    }

    public void setUnitPriceDeviation(BigDecimal unitPriceDeviation) {
        this.deviation = unitPriceDeviation;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getRejectedExpireTime() {
        if (!"REJECTED".equals(auditStatus) || auditTime == null) {
            return null;
        }
        return auditTime.plusHours(24);
    }

    public Long getRejectedRemainingSeconds() {
        LocalDateTime expireTime = getRejectedExpireTime();
        if (expireTime == null) {
            return null;
        }
        long seconds = Duration.between(LocalDateTime.now(), expireTime).getSeconds();
        return Math.max(seconds, 0);
    }
}
