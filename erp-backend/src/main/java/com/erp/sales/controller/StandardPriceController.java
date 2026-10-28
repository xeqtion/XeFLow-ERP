package com.erp.sales.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.sales.dto.StandardPriceExcel;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.service.StandardPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品报价 - CRUD + 分页 + Excel 导入导出
 */
@Tag(name = "产品报价")
@RestController
@RequestMapping("/sales/standard-price")
public class StandardPriceController {

    private final StandardPriceService service;

    public StandardPriceController(StandardPriceService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('standard_price:list')")
    @GetMapping("/page")
    public Result<PageResult<StandardPrice>> page(PageQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "产品报价", operation = "新建报价")
    @PreAuthorize("@perm.has('standard_price:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody StandardPrice entity) {
        return Result.id(service.saveQuote(entity));
    }

    @Operation(summary = "修改")
    @OperationLog(module = "产品报价", operation = "修改报价")
    @PreAuthorize("@perm.has('standard_price:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody StandardPrice entity) {
        service.updateQuote(entity);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "产品报价", operation = "删除报价")
    @PreAuthorize("@perm.has('standard_price:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @OperationLog(module = "产品报价", operation = "导入报价")
    @PreAuthorize("@perm.has('standard_price:import')")
    @PostMapping("/import")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        return Result.success(service.importExcel(file));
    }

    @Operation(summary = "Excel 导出")
    @PreAuthorize("@perm.has('standard_price:export')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<StandardPriceExcel> data = service.listByKeyword(keyword).stream()
                .map(s -> {
                    StandardPriceExcel e = new StandardPriceExcel();
                    BeanUtils.copyProperties(s, e);
                    return e;
                }).collect(Collectors.toList());
        ExcelUtil.export(response, "产品报价", "产品报价", StandardPriceExcel.class, data);
    }

    @Operation(summary = "下载导入模板")
    @PreAuthorize("@perm.has('standard_price:export')")
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.template(response, "产品报价导入模板", StandardPriceExcel.class);
    }
}
