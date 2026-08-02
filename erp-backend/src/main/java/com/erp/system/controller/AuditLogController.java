package com.erp.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.api.Result;
import com.erp.common.audit.AuditLog;
import com.erp.common.audit.AuditLogMapper;
import com.erp.common.audit.AuditLogQuery;
import com.erp.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志 - 只读分页查询 (按操作人/模块/结果/日期)
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/system/audit")
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    public AuditLogController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Operation(summary = "分页查询审计日志")
    @PreAuthorize("@perm.has('system:audit:list')")
    @GetMapping("/page")
    public Result<PageResult<AuditLog>> page(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .like(StringUtils.hasText(query.getUsername()), AuditLog::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getModule()), AuditLog::getModule, query.getModule())
                .eq(query.getSuccess() != null, AuditLog::getSuccess, query.getSuccess())
                .ge(StringUtils.hasText(query.getStartDate()), AuditLog::getCreateTime,
                        query.getStartDate() + " 00:00:00")
                .le(StringUtils.hasText(query.getEndDate()), AuditLog::getCreateTime,
                        query.getEndDate() + " 23:59:59")
                .orderByDesc(AuditLog::getCreateTime);
        Page<AuditLog> page = auditLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return Result.success(PageResult.of(page));
    }
}
