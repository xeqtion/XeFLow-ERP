package com.erp.basedata.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 销售主管或管理员将个人客户转回公共客户池。 */
@Data
public class CustomerPoolTransferDTO {

    @Size(max = 500, message = "转移理由不能超过 500 个字符")
    private String reason;
}
