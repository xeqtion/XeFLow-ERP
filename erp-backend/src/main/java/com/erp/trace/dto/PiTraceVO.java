package com.erp.trace.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PI 单据追踪结果 (主单 + 时间线)
 */
@Data
public class PiTraceVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long piId;
    private String piNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    private String ownerName;
    private String customerCode;
    private String customer;
    private String currency;
    private BigDecimal totalAmount;
    private String status;
    private String signDate;
    private String deliveryDate;
    private String paymentMethod;
    private String tradeTerm;

    private List<PiTraceItem> items;
    private List<TimelineEvent> events;
}
