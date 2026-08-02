package com.erp.common.security;

import org.springframework.stereotype.Service;

/**
 * 权限判断 - 供 @PreAuthorize("@perm.has('模块:操作')") 使用。
 * 超级管理员持有通配权限 "*", 命中任意权限。
 */
@Service("perm")
public class PermissionService {

    public static final String ALL_PERMISSION = "*";

    /** 当前用户是否拥有指定权限 */
    public boolean has(String permission) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user == null || permission == null) {
            return false;
        }
        return user.getPermissions().contains(ALL_PERMISSION)
                || user.getPermissions().contains(permission);
    }

    /** 是否拥有其中任意一个权限 */
    public boolean hasAny(String... permissions) {
        for (String p : permissions) {
            if (has(p)) {
                return true;
            }
        }
        return false;
    }
}
