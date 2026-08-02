package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称, 如: 销售主管 */
    private String roleName;

    /** 角色标识, 如: sales_manager */
    private String roleKey;

    /** 排序 */
    private Integer sort;

    /** 状态: 1 启用, 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
