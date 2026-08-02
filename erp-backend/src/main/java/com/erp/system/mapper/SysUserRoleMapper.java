package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT DISTINCT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
