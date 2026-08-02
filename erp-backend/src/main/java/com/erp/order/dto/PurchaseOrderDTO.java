package com.erp.order.dto;

import com.erp.order.entity.PurchaseOrder;
import com.erp.order.entity.PurchaseOrderItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * PO 主表 + 明细 (用于新建/修改/详情)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderDTO extends PurchaseOrder {

    private List<PurchaseOrderItem> items = new ArrayList<>();
}
