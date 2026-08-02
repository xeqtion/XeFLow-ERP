package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 汇率 - 1 外币 = rateToBase 本位币 (本位币默认 CNY, 由 erp.currency.base 配置)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("currency_rate")
public class CurrencyRate extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 外币代码, 如 USD/EUR */
    private String currency;

    /** Optional Chinese display name for manually maintained currencies. */
    private String currencyName;

    /** 1 外币 折合的本位币金额 */
    private BigDecimal rateToBase;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    private String remark;
}
