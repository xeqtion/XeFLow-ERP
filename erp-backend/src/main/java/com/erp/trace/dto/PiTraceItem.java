package com.erp.trace.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单据追踪中的 PI 明细行。
 */
@Data
public class PiTraceItem {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    private String prodCode;
    private String productName;
    private String productModel;
    private String factoryModel;
    private String unit;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long quoteId;

    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
}
