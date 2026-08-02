package com.erp.finance.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageResult;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.dto.FinanceEntryExcel;
import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.dto.FinanceSummary;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.service.FinanceEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收付款管理 - 6 类收付款录入/查询/导入导出 + 按类型汇总
 */
@Tag(name = "收付款管理")
@RestController
@RequestMapping("/finance/entry")
public class FinanceEntryController {

    private final FinanceEntryService service;

    public FinanceEntryController(FinanceEntryService service) {
        this.service = service;
    }

    @Operation(summary = "类型字典 (6 类)")
    @PreAuthorize("@perm.has('finance:list')")
    @GetMapping("/types")
    public Result<List<Map<String, String>>> types() {
        List<Map<String, String>> list = new ArrayList<>();
        for (FinanceType t : FinanceType.values()) {
            list.add(Map.of("code", t.name(), "label", t.getLabel(), "direction", t.getDirection()));
        }
        return Result.success(list);
    }

    @Operation(summary = "分页查询 (可按类型/客户/日期过滤)")
    @PreAuthorize("@perm.has('finance:list')")
    @GetMapping("/page")
    public Result<PageResult<FinanceEntry>> page(FinanceEntryQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "按收付方向和币种汇总")
    @PreAuthorize("@perm.has('finance:list')")
    @GetMapping("/summary")
    public Result<List<FinanceSummary>> summary(FinanceEntryQuery query) {
        return Result.success(service.summary(query));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "收付款", operation = "新建收付款")
    @PreAuthorize("@perm.has('finance:create')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FinanceEntry entry) {
        validate(entry);
        entry.setId(null);
        service.fillDirection(entry);
        service.save(entry);
        return Result.success(entry.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "收付款", operation = "修改收付款")
    @PreAuthorize("@perm.has('finance:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody FinanceEntry entry) {
        if (entry.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        validate(entry);
        service.fillDirection(entry);
        service.updateById(entry);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "收付款", operation = "删除收付款")
    @PreAuthorize("@perm.has('finance:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @OperationLog(module = "收付款", operation = "导入收付款")
    @PreAuthorize("@perm.has('finance:import')")
    @PostMapping("/import")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        return Result.success(service.importExcel(file));
    }

    @Operation(summary = "Excel 导出 (按过滤条件)")
    @PreAuthorize("@perm.has('finance:export')")
    @GetMapping("/export")
    public void export(FinanceEntryQuery query, HttpServletResponse response) throws IOException {
        List<FinanceEntryExcel> data = service.listForExport(query).stream().map(e -> {
            FinanceEntryExcel x = new FinanceEntryExcel();
            BeanUtils.copyProperties(e, x);
            x.setCustomerCode(e.getCustomerCode());
            return x;
        }).collect(Collectors.toList());
        ExcelUtil.export(response, "收付款流水", "收付款", FinanceEntryExcel.class, data);
    }

    @Operation(summary = "下载导入模板")
    @PreAuthorize("@perm.has('finance:export')")
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.template(response, "收付款导入模板", FinanceEntryExcel.class);
    }

    private void validate(FinanceEntry entry) {
        if (FinanceType.of(entry.getType()) == null) {
            throw new BusinessException("类型非法");
        }
        if (entry.getAmount() == null) {
            throw new BusinessException("金额不能为空");
        }
        if (!StringUtils.hasText(entry.getCurrency())) {
            entry.setCurrency("USD");
        }
    }
}
