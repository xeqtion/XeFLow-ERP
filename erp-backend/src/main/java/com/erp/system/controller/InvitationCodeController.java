package com.erp.system.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.system.dto.InvitationCodeCreateDTO;
import com.erp.system.dto.InvitationCodeVO;
import com.erp.system.service.InvitationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "邀请码管理")
@RestController
@RequestMapping("/system/user/invitations")
public class InvitationCodeController {

    private final InvitationCodeService invitationCodeService;

    public InvitationCodeController(InvitationCodeService invitationCodeService) {
        this.invitationCodeService = invitationCodeService;
    }

    @Operation(summary = "分页查询邀请码")
    @PreAuthorize("@perm.has('system:user:invite:list')")
    @GetMapping("/page")
    public Result<PageResult<InvitationCodeVO>> page(PageQuery query) {
        return Result.success(PageResult.of(invitationCodeService.pageVO(query)));
    }

    @Operation(summary = "自动生成邀请码")
    @OperationLog(module = "用户管理", operation = "生成邀请码")
    @PreAuthorize("@perm.has('system:user:invite:create')")
    @PostMapping
    public Result<InvitationCodeVO> create(@Valid @RequestBody InvitationCodeCreateDTO dto) {
        return Result.success(invitationCodeService.createCode(dto));
    }

    @Operation(summary = "删除邀请码")
    @OperationLog(module = "用户管理", operation = "删除邀请码")
    @PreAuthorize("@perm.has('system:user:invite:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        invitationCodeService.deleteCode(id);
        return Result.success();
    }
}
