package com.erp.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.system.dto.AssignPermissionsDTO;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 - 列表 / 分页 / CRUD / 分配权限
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
public class SysRoleController {

    private final SysRoleService roleService;

    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "角色列表")
    @PreAuthorize("@perm.has('system:role:list')")
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list(
                Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId)));
    }

    @Operation(summary = "角色分页")
    @PreAuthorize("@perm.has('system:role:list')")
    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(PageQuery query) {
        return Result.success(PageResult.of(roleService.pageQuery(query)));
    }

    @Operation(summary = "角色已分配的权限ID")
    @PreAuthorize("@perm.has('system:role:list')")
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> permissions(@PathVariable Long id) {
        return Result.success(roleService.permissionIds(id));
    }

    @Operation(summary = "新建角色")
    @OperationLog(module = "角色管理", operation = "新建角色")
    @PreAuthorize("@perm.has('system:role:create')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysRole role) {
        roleService.createRole(role);
        return Result.success();
    }

    @Operation(summary = "修改角色")
    @OperationLog(module = "角色管理", operation = "修改角色")
    @PreAuthorize("@perm.has('system:role:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysRole role) {
        roleService.updateRole(role);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @OperationLog(module = "角色管理", operation = "删除角色")
    @PreAuthorize("@perm.has('system:role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "给角色分配权限")
    @OperationLog(module = "角色管理", operation = "分配权限")
    @PreAuthorize("@perm.has('system:role:update')")
    @PutMapping("/{id}/permissions")
    public Result<Void> assign(@PathVariable Long id, @RequestBody AssignPermissionsDTO dto) {
        roleService.assignPermissions(id, dto.getPermissionIds());
        return Result.success();
    }
}
