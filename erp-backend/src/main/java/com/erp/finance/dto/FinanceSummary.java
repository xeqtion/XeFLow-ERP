package com.erp.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "按类型汇总")
public class FinanceSummary {

    private String type;
    private String label;
    private String direction;
    /** 汇总金额的币种。 */
    private String currency;
    private BigDecimal totalAmount;
    private long count;
}
