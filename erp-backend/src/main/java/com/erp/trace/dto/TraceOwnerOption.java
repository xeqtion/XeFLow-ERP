package com.erp.trace.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 单据追踪可筛选的合同担当。
 */
@Data
public class TraceOwnerOption {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    public TraceOwnerOption(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
