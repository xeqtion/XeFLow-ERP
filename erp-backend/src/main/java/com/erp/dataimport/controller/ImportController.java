package com.erp.dataimport.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.dataimport.dto.EtlResult;
import com.erp.dataimport.dto.SheetPreview;
import com.erp.dataimport.entity.ImportBatch;
import com.erp.dataimport.entity.ImportRow;
import com.erp.dataimport.service.EtlService;
import com.erp.dataimport.service.ExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 通用数据导入 - 上传 Excel, 选 sheet/表头, 整表入库为可浏览的结构化数据
 */
@Tag(name = "数据导入")
@RestController
@ConditionalOnProperty(name = "erp.dataimport.enabled", havingValue = "true")
@RequestMapping("/dataimport")
public class ImportController {

    private final ExcelImportService importService;
    private final EtlService etlService;

    public ImportController(ExcelImportService importService, EtlService etlService) {
        this.importService = importService;
        this.etlService = etlService;
    }

    @Operation(summary = "探查 Excel(列出 sheet 与样例行)")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @PostMapping("/inspect")
    public Result<List<SheetPreview>> inspect(@RequestParam("file") MultipartFile file) {
        return Result.success(importService.inspect(file));
    }

    @Operation(summary = "导入指定 sheet")
    @OperationLog(module = "数据导入", operation = "导入Excel")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @PostMapping("/commit")
    public Result<ImportBatch> commit(@RequestParam("file") MultipartFile file,
                                      @RequestParam String sheetName,
                                      @RequestParam(defaultValue = "0") int headerRowIndex) {
        return Result.success("导入成功", importService.commit(file, sheetName, headerRowIndex));
    }

    @Operation(summary = "导入批次分页")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @GetMapping("/batches")
    public Result<PageResult<ImportBatch>> batches(PageQuery query) {
        return Result.success(PageResult.of(importService.pageBatches(query)));
    }

    @Operation(summary = "批次数据行分页")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @GetMapping("/batch/{id}/rows")
    public Result<PageResult<ImportRow>> rows(@PathVariable Long id, PageQuery query) {
        return Result.success(PageResult.of(importService.pageRows(id, query)));
    }

    @Operation(summary = "删除批次")
    @OperationLog(module = "数据导入", operation = "删除导入批次")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @DeleteMapping("/batch/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        importService.deleteBatch(id);
        return Result.success();
    }

    @Operation(summary = "结构化ETL: 批次转客户档案")
    @OperationLog(module = "数据导入", operation = "ETL转客户")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @PostMapping("/etl/customers/{batchId}")
    public Result<EtlResult> etlCustomers(@PathVariable Long batchId) {
        return Result.success("ETL完成", etlService.importCustomers(batchId));
    }

    @Operation(summary = "结构化ETL: 批次转产品档案")
    @OperationLog(module = "数据导入", operation = "ETL转产品")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @PostMapping("/etl/products/{batchId}")
    public Result<EtlResult> etlProducts(@PathVariable Long batchId) {
        return Result.success("ETL完成", etlService.importProducts(batchId));
    }

    @Operation(summary = "结构化ETL: 批次转销售PI(主+明细)")
    @OperationLog(module = "数据导入", operation = "ETL转PI")
    @PreAuthorize("@perm.has('dataimport:manage')")
    @PostMapping("/etl/pis/{batchId}")
    public Result<EtlResult> etlPis(@PathVariable Long batchId) {
        return Result.success("ETL完成", etlService.importPis(batchId));
    }
}
