package com.erp.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.security.LoginUser;
import com.erp.common.security.PermissionService;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录认证时加载用户 - 供 AuthenticationManager 使用
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;

    public UserDetailsServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        Set<String> permissions = new HashSet<>(userMapper.selectPermKeysByUserId(user.getId()));
        // 超级管理员(admin 角色)授予通配权限
        List<String> roleKeys = userMapper.selectRoleKeysByUserId(user.getId());
        if (roleKeys.contains("super_admin") || roleKeys.contains("admin")) {
            permissions.add(PermissionService.ALL_PERMISSION);
        }
        return new LoginUser(user, permissions);
    }
}
