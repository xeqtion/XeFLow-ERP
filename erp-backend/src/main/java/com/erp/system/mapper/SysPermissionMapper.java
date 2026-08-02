package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 查询用户可见的菜单 (目录/菜单, 不含按钮) */
    @Select("""
            SELECT DISTINCT p.*
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.type IN ('M', 'C')
              AND p.status = 1 AND p.deleted = 0
            ORDER BY p.sort
            """)
    List<SysPermission> selectMenusByUserId(@Param("userId") Long userId);
}
