package com.erp.order.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Partial or full PO receipt request. */
@Data
public class PoReceiptDTO {
    private List<PoReceiptItemDTO> items = new ArrayList<>();
}
