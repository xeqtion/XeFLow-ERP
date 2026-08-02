package com.erp.basedata.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 可被分配为客户担当的业务员下拉项。 */
@Data
@AllArgsConstructor
public class CustomerAssigneeOption {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String username;
}
