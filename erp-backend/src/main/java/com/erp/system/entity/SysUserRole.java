package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色 关联
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    private Long userId;

    private Long roleId;
}
