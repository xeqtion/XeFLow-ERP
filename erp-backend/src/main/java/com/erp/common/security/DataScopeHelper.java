package com.erp.common.security;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 数据范围 - 业务员只看自己担当(owner_id=本人)的单据;
 * 拥有 data:all 权限(或超管通配 *)的角色看全部。
 */
@Component
public class DataScopeHelper {

    public static final String VIEW_ALL = "data:all";

    /** 是否可查看全部数据 */
    public boolean viewAll() {
        LoginUser u = SecurityUtils.getLoginUser();
        if (u == null) {
            return true; // 非 web 上下文(定时任务等)不限制
        }
        Set<String> perms = u.getPermissions();
        return perms.contains(PermissionService.ALL_PERMISSION) || perms.contains(VIEW_ALL);
    }

    /** 需要限制时返回当前用户ID, 否则返回 null(不限制) */
    public Long restrictOwnerId() {
        return viewAll() ? null : SecurityUtils.getCurrentUserId();
    }
}
