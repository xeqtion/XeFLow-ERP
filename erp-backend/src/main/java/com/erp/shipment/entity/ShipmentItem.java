package com.erp.shipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** Product allocation recorded for each generated shipment. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shipment_item")
public class ShipmentItem extends BaseEntity {
    private Long shipmentId;
    private Long piItemId;
    private Long productId;
    private String prodCode;
    private BigDecimal qty;
}
