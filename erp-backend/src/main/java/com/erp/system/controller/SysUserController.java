package com.erp.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageResult;
import com.erp.system.dto.ChangePasswordDTO;
import com.erp.system.dto.ResetPasswordDTO;
import com.erp.system.dto.SysUserQuery;
import com.erp.system.dto.SysUserSaveDTO;
import com.erp.system.dto.SysUserVO;
import com.erp.system.entity.SysRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 - CRUD + 角色分配 + 重置密码 + 启用禁用 + 自助改密
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    private final SysUserService userService;
    private final SysRoleMapper roleMapper;

    public SysUserController(SysUserService userService, SysRoleMapper roleMapper) {
        this.userService = userService;
        this.roleMapper = roleMapper;
    }

    @Operation(summary = "分页查询用户")
    @PreAuthorize("@perm.has('system:user:list')")
    @GetMapping("/page")
    public Result<PageResult<SysUserVO>> page(SysUserQuery query) {
        return Result.success(PageResult.of(userService.pageVO(query)));
    }

    @Operation(summary = "用户详情")
    @PreAuthorize("@perm.has('system:user:list')")
    @GetMapping("/{id}")
    public Result<SysUserVO> get(@PathVariable Long id) {
        return Result.success(userService.getVO(id));
    }

    @Operation(summary = "可分配角色列表 (供用户表单下拉)")
    @PreAuthorize("@perm.has('system:user:list')")
    @GetMapping("/roles")
    public Result<List<SysRole>> assignableRoles() {
        return Result.success(roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getStatus, 1)
                        .ne(SysRole::getRoleKey, "super_admin")
                        .orderByAsc(SysRole::getId)));
    }

    @Operation(summary = "新建用户")
    @OperationLog(module = "用户管理", operation = "新建用户")
    @PreAuthorize("@perm.has('system:user:create')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysUserSaveDTO dto) {
        dto.setId(null);
        userService.saveUser(dto);
        return Result.success();
    }

    @Operation(summary = "修改用户")
    @OperationLog(module = "用户管理", operation = "修改用户")
    @PreAuthorize("@perm.has('system:user:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysUserSaveDTO dto) {
        userService.updateUser(dto);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @OperationLog(module = "用户管理", operation = "删除用户")
    @PreAuthorize("@perm.has('system:user:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "启用/停用用户 (审核启用)")
    @OperationLog(module = "用户管理", operation = "启用停用")
    @PreAuthorize("@perm.has('system:user:update')")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置密码 (管理员)")
    @OperationLog(module = "用户管理", operation = "重置密码")
    @PreAuthorize("@perm.has('system:user:resetPwd')")
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.success();
    }

    @Operation(summary = "修改本人密码")
    @OperationLog(module = "用户管理", operation = "修改本人密码")
    @PutMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }
}
