package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.security.LoginUser;
import com.erp.common.security.PermissionService;
import com.erp.common.security.SecurityUtils;
import com.erp.system.dto.MenuVO;
import com.erp.system.entity.SysPermission;
import com.erp.system.mapper.SysPermissionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单服务 - 按当前用户权限构建菜单树
 */
@Service
public class MenuService {

    private final SysPermissionMapper permissionMapper;

    public MenuService(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    public List<MenuVO> getCurrentUserMenuTree() {
        Long userId = SecurityUtils.getCurrentUserId();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        List<SysPermission> menus = loginUser != null
                && loginUser.getPermissions().contains(PermissionService.ALL_PERMISSION)
                ? permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getType, List.of("M", "C"))
                        .eq(SysPermission::getStatus, 1)
                        .orderByAsc(SysPermission::getSort))
                : permissionMapper.selectMenusByUserId(userId);
        List<MenuVO> nodes = menus.stream().map(this::toVO).collect(Collectors.toList());
        return buildTree(nodes);
    }

    private MenuVO toVO(SysPermission p) {
        MenuVO vo = new MenuVO();
        vo.setId(p.getId());
        vo.setParentId(p.getParentId());
        vo.setName(p.getPermName());
        vo.setPermKey(p.getPermKey());
        vo.setPath(p.getPath());
        vo.setComponent(p.getComponent());
        vo.setIcon(p.getIcon());
        vo.setSort(p.getSort());
        return vo;
    }

    private List<MenuVO> buildTree(List<MenuVO> nodes) {
        Map<Long, MenuVO> byId = nodes.stream()
                .collect(Collectors.toMap(MenuVO::getId, n -> n));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : nodes) {
            MenuVO parent = byId.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
