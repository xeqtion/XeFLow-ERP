package com.erp.basedata.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 销售主管从公共客户池分配跟单业务员。 */
@Data
public class CustomerAssignDTO {

    @NotNull(message = "请选择跟单人")
    private Long ownerId;
}
