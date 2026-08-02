package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.system.entity.SysPermission;
import com.erp.system.entity.SysRolePermission;
import com.erp.system.mapper.SysPermissionMapper;
import com.erp.system.mapper.SysRolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 权限/菜单服务 - CRUD (删除级联清理角色授权)
 */
public interface SysPermissionService extends IService<SysPermission> {

    List<SysPermission> listAll();

    void createPermission(SysPermission permission);

    void updatePermission(SysPermission permission);

    void deletePermission(Long id);

    @Service
    class Impl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

        private final SysRolePermissionMapper rolePermMapper;

        public Impl(SysRolePermissionMapper rolePermMapper) {
            this.rolePermMapper = rolePermMapper;
        }

        @Override
        public List<SysPermission> listAll() {
            return list(Wrappers.<SysPermission>lambdaQuery().orderByAsc(SysPermission::getSort));
        }

        @Override
        public void createPermission(SysPermission permission) {
            permission.setId(null);
            normalize(permission);
            checkKeyUnique(permission.getPermKey(), null);
            save(permission);
        }

        @Override
        public void updatePermission(SysPermission permission) {
            if (permission.getId() == null) {
                throw new BusinessException("缺少权限ID");
            }
            if (permission.getId().equals(permission.getParentId())) {
                throw new BusinessException("父级不能是自身");
            }
            normalize(permission);
            checkKeyUnique(permission.getPermKey(), permission.getId());
            updateById(permission);
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void deletePermission(Long id) {
            Long children = count(Wrappers.<SysPermission>lambdaQuery().eq(SysPermission::getParentId, id));
            if (children != null && children > 0) {
                throw new BusinessException("存在下级权限, 请先删除下级");
            }
            removeById(id);
            rolePermMapper.delete(Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getPermissionId, id));
        }

        private void normalize(SysPermission p) {
            if (!StringUtils.hasText(p.getPermName())) {
                throw new BusinessException("名称不能为空");
            }
            if (!StringUtils.hasText(p.getType())) {
                throw new BusinessException("类型不能为空 (M/C/B)");
            }
            if (p.getParentId() == null) {
                p.setParentId(0L);
            }
            if (p.getStatus() == null) {
                p.setStatus(1);
            }
            if (p.getSort() == null) {
                p.setSort(0);
            }
        }

        private void checkKeyUnique(String permKey, Long excludeId) {
            if (!StringUtils.hasText(permKey)) {
                return;
            }
            LambdaQueryWrapper<SysPermission> w = Wrappers.<SysPermission>lambdaQuery()
                    .eq(SysPermission::getPermKey, permKey);
            if (excludeId != null) {
                w.ne(SysPermission::getId, excludeId);
            }
            if (count(w) > 0) {
                throw new BusinessException("权限标识已存在: " + permKey);
            }
        }
    }
}
