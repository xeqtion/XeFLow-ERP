package com.erp.trace.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * PI 简要 (供单据追踪选择)
 */
@Data
public class PiBrief {
    /** 雪花ID序列化为字符串, 防前端精度丢失致选 PI 后查询"PI 不存在" */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String piNo;
    private String customer;
    private String signDate;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    private String ownerName;

    public PiBrief(Long id, String piNo, String customer, String signDate, Long ownerId, String ownerName) {
        this.id = id;
        this.piNo = piNo;
        this.customer = customer;
        this.signDate = signDate;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
    }
}
