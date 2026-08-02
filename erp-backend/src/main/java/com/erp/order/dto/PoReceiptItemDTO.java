package com.erp.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/** One PO line's quantity received in the current operation. */
@Data
public class PoReceiptItemDTO {
    private Long itemId;
    private BigDecimal receivedQty;
}
