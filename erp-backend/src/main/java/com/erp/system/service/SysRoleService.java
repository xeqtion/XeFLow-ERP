package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.TokenService;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysRolePermission;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRolePermissionMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 角色服务 - CRUD + 权限分配
 */
public interface SysRoleService extends IService<SysRole> {

    /** 内置超级管理员角色 ID, 受保护 */
    long SUPER_ADMIN_ROLE_ID = 0L;
    long ADMIN_ROLE_ID = 1L;

    IPage<SysRole> pageQuery(PageQuery query);

    void createRole(SysRole role);

    void updateRole(SysRole role);

    void deleteRole(Long id);

    List<Long> permissionIds(Long roleId);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    @Service
    class Impl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

        private final SysRolePermissionMapper rolePermMapper;
        private final SysUserRoleMapper userRoleMapper;
        private final TokenService tokenService;

        public Impl(SysRolePermissionMapper rolePermMapper, SysUserRoleMapper userRoleMapper,
                    TokenService tokenService) {
            this.rolePermMapper = rolePermMapper;
            this.userRoleMapper = userRoleMapper;
            this.tokenService = tokenService;
        }

        @Override
        public IPage<SysRole> pageQuery(PageQuery query) {
            LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                    .orderByAsc(SysRole::getId);
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                wrapper.and(w -> w.like(SysRole::getRoleName, kw).or().like(SysRole::getRoleKey, kw));
            }
            return page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        }

        @Override
        public void createRole(SysRole role) {
            role.setId(null);
            role.setSort(0);
            checkKeyUnique(role.getRoleKey(), null);
            if (role.getStatus() == null) {
                role.setStatus(1);
            }
            save(role);
        }

        @Override
        public void updateRole(SysRole role) {
            if (role.getId() == null) {
                throw new BusinessException("缺少角色ID");
            }
            if (role.getId() == SUPER_ADMIN_ROLE_ID) {
                role.setRoleKey("super_admin");
                role.setStatus(1);
            } else if (role.getId() == ADMIN_ROLE_ID) {
                // 内置超管角色禁止改标识/禁用
                role.setRoleKey("admin");
                role.setStatus(1);
            }
            checkKeyUnique(role.getRoleKey(), role.getId());
            role.setSort(null);
            updateById(role);
            invalidateUsersForRole(role.getId());
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void deleteRole(Long id) {
            if (id == SUPER_ADMIN_ROLE_ID || id == ADMIN_ROLE_ID) {
                throw new BusinessException("内置超级管理员角色不可删除");
            }
            Long inUse = userRoleMapper.selectCount(
                    Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
            if (inUse != null && inUse > 0) {
                throw new BusinessException("该角色已分配给 " + inUse + " 个用户, 请先解除后再删除");
            }
            removeById(id);
            rolePermMapper.delete(Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, id));
        }

        @Override
        public List<Long> permissionIds(Long roleId) {
            return rolePermMapper.selectList(
                            Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId))
                    .stream().map(SysRolePermission::getPermissionId).toList();
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void assignPermissions(Long roleId, List<Long> permissionIds) {
            if (getById(roleId) == null) {
                throw new BusinessException("角色不存在");
            }
            rolePermMapper.delete(Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
            if (permissionIds != null) {
                permissionIds.stream().filter(Objects::nonNull).distinct().forEach(pid -> {
                    SysRolePermission rp = new SysRolePermission();
                    rp.setRoleId(roleId);
                    rp.setPermissionId(pid);
                    rolePermMapper.insert(rp);
                });
            }
            invalidateUsersForRole(roleId);
        }

        private void invalidateUsersForRole(Long roleId) {
            userRoleMapper.selectUserIdsByRoleId(roleId).forEach(tokenService::invalidateUser);
        }

        private void checkKeyUnique(String roleKey, Long excludeId) {
            if (!StringUtils.hasText(roleKey)) {
                throw new BusinessException("角色标识不能为空");
            }
            LambdaQueryWrapper<SysRole> w = Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleKey, roleKey);
            if (excludeId != null) {
                w.ne(SysRole::getId, excludeId);
            }
            if (count(w) > 0) {
                throw new BusinessException("角色标识已存在: " + roleKey);
            }
        }
    }
}
