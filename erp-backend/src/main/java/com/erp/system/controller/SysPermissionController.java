package com.erp.system.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.system.entity.SysPermission;
import com.erp.system.service.SysPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限菜单 - 全量列表(前端建树) + CRUD
 */
@Tag(name = "权限菜单")
@RestController
@RequestMapping("/system/permission")
public class SysPermissionController {

    private final SysPermissionService permissionService;

    public SysPermissionController(SysPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "权限/菜单全量列表 (按排序)")
    @PreAuthorize("@perm.has('system:permission:list')")
    @GetMapping("/list")
    public Result<List<SysPermission>> list() {
        return Result.success(permissionService.listAll());
    }

    @Operation(summary = "新建权限/菜单")
    @OperationLog(module = "权限菜单", operation = "新建权限")
    @PreAuthorize("@perm.has('system:permission:create')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysPermission permission) {
        permissionService.createPermission(permission);
        return Result.success();
    }

    @Operation(summary = "修改权限/菜单")
    @OperationLog(module = "权限菜单", operation = "修改权限")
    @PreAuthorize("@perm.has('system:permission:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysPermission permission) {
        permissionService.updatePermission(permission);
        return Result.success();
    }

    @Operation(summary = "删除权限/菜单")
    @OperationLog(module = "权限菜单", operation = "删除权限")
    @PreAuthorize("@perm.has('system:permission:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }
}
