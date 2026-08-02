package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.dto.UserRoleItem;
import com.erp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 查询用户已分配的角色ID */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 批量查询多个用户的角色 (用于列表展示, 避免 N+1) */
    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.id AS roleId, r.role_name AS roleName, r.role_key AS roleKey
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id IN
            <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ORDER BY r.id
            </script>
            """)
    List<UserRoleItem> selectRolesByUserIds(@Param("userIds") Collection<Long> userIds);

    /** 查询用户拥有的权限标识 (perm_key) */
    @Select("""
            SELECT DISTINCT p.perm_key
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.status = 1 AND p.deleted = 0
              AND p.perm_key IS NOT NULL AND p.perm_key <> ''
            """)
    List<String> selectPermKeysByUserId(@Param("userId") Long userId);

    /** 查询用户拥有的角色标识 (role_key) */
    @Select("""
            SELECT r.role_key
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1 AND r.deleted = 0
            """)
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 查询指定角色下的启用用户 */
    @Select("""
            SELECT DISTINCT u.*
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_key = #{roleKey}
              AND r.status = 1 AND r.deleted = 0
              AND u.status = 1 AND u.deleted = 0
            """)
    List<SysUser> selectActiveUsersByRoleKey(@Param("roleKey") String roleKey);

    /** Query active users under any of the given role keys. */
    @Select("""
            <script>
            SELECT DISTINCT u.*
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_key IN
            <foreach collection='roleKeys' item='roleKey' open='(' separator=',' close=')'>#{roleKey}</foreach>
              AND r.status = 1 AND r.deleted = 0
              AND u.status = 1 AND u.deleted = 0
            </script>
            """)
    List<SysUser> selectActiveUsersByRoleKeys(@Param("roleKeys") Collection<String> roleKeys);
}
