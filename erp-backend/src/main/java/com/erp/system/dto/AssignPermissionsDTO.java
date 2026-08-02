package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 给角色分配权限
 */
@Data
@Schema(description = "分配权限入参")
public class AssignPermissionsDTO {

    @Schema(description = "权限ID列表")
    private List<Long> permissionIds;
}
