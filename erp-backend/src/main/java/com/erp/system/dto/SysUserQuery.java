package com.erp.system.dto;

import com.erp.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询")
public class SysUserQuery extends PageQuery {

    @Schema(description = "状态过滤 1启用 0禁用, 空=全部")
    private Integer status;
}
