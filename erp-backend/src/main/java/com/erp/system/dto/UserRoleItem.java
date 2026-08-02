package com.erp.system.dto;

import lombok.Data;

/**
 * 用户-角色 批量查询投影
 */
@Data
public class UserRoleItem {
    private Long userId;
    private Long roleId;
    private String roleName;
    private String roleKey;
}
