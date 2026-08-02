package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限 关联
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    private Long roleId;

    private Long permissionId;
}
