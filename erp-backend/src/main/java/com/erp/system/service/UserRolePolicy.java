package com.erp.system.service;

import com.erp.common.exception.BusinessException;
import com.erp.system.entity.SysRole;

import java.util.List;

/** Central policy for assigning multiple roles to a user. */
public final class UserRolePolicy {

    public static final long ADMIN_USER_ID = 1L;
    public static final long SUPER_ADMIN_ROLE_ID = 0L;
    public static final int MAX_ROLES = 3;

    private UserRolePolicy() {
    }

    public static List<Long> normalize(Long userId, List<SysRole> roles) {
        if (Long.valueOf(ADMIN_USER_ID).equals(userId)) {
            return List.of(SUPER_ADMIN_ROLE_ID);
        }
        if (roles.stream().anyMatch(role -> "super_admin".equals(role.getRoleKey()))) {
            throw new BusinessException("超级管理员角色仅限内置 admin 账号使用");
        }

        SysRole admin = roles.stream()
                .filter(role -> "admin".equals(role.getRoleKey()))
                .findFirst()
                .orElse(null);
        if (admin != null) {
            return List.of(admin.getId());
        }

        boolean hasReadonly = roles.stream().anyMatch(role -> "readonly".equals(role.getRoleKey()));
        if (hasReadonly && roles.size() > 1) {
            throw new BusinessException("只读权限不能与其他角色同时分配");
        }
        if (roles.size() > MAX_ROLES) {
            throw new BusinessException("每个用户最多可同时分配 3 个角色");
        }
        return roles.stream().map(SysRole::getId).toList();
    }
}
