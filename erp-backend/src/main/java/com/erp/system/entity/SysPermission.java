package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限 / 菜单
 * 权限粒度: 模块:操作, 例如 contract_price:approve、customer:export
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 父级 ID, 顶级为 0 */
    private Long parentId;

    /** 名称, 如: 客户资料 */
    private String permName;

    /** 权限标识, 如: customer:export */
    private String permKey;

    /** 类型: M 目录, C 菜单, B 按钮 */
    private String type;

    /** 前端路由路径 */
    private String path;

    /** 前端组件 */
    private String component;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 状态: 1 启用, 0 禁用 */
    private Integer status;
}
