package com.erp.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存结存行 - 产品当前结存 + 安全库存 + 预警。
 */
@Data
public class StockRow {

    private Long productId;
    private String prodCode;
    private String prodName;
    private String unit;
    /** 当前结存 = IN - OUT */
    private BigDecimal currentStock;
    /** 安全库存 */
    private BigDecimal safetyStock;
    /** 是否低于安全库存 */
    private Boolean lowStock;
}
