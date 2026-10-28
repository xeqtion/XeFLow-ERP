package com.erp.basedata.controller;

import com.erp.basedata.dto.BomExcel;
import com.erp.basedata.entity.Bom;
import com.erp.basedata.service.BomService;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 产品配件资料 CRUD + 分页筛选。
 */
@Tag(name = "产品配件资料")
@RestController
@RequestMapping("/basedata/bom")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('bom:list')")
    @GetMapping("/page")
    public Result<PageResult<Bom>> page(PageQuery query,
                                        @RequestParam(required = false) String productModel,
                                        @RequestParam(required = false) Boolean universalOnly) {
        return Result.success(PageResult.of(bomService.pageQuery(query, productModel, universalOnly)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('bom:list')")
    @GetMapping("/{id}")
    public Result<Bom> get(@PathVariable Long id) {
        return Result.success(bomService.getDetail(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "产品配件资料", operation = "新建配件")
    @PreAuthorize("@perm.has('bom:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody Bom bom) {
        return Result.id(bomService.saveBom(bom));
    }

    @Operation(summary = "修改")
    @OperationLog(module = "产品配件资料", operation = "修改配件")
    @PreAuthorize("@perm.has('bom:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody Bom bom) {
        bomService.updateBom(bom);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "产品配件资料", operation = "删除配件")
    @PreAuthorize("@perm.has('bom:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bomService.removeBom(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @OperationLog(module = "产品配件资料", operation = "导入配件")
    @PreAuthorize("@perm.has('bom:import')")
    @PostMapping("/import")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }
        return Result.success(bomService.importExcel(file));
    }

    @Operation(summary = "Excel 导出")
    @PreAuthorize("@perm.has('bom:export')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<BomExcel> data = bomService.listByKeyword(keyword).stream()
                .map(BomController::toExcel)
                .toList();
        ExcelUtil.export(response, "产品配件资料", "配件", BomExcel.class, data);
    }

    @Operation(summary = "下载导入模板")
    @PreAuthorize("@perm.has('bom:export')")
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.template(response, "产品配件资料导入模板", BomExcel.class);
    }

    private static BomExcel toExcel(Bom bom) {
        BomExcel row = new BomExcel();
        row.setBomCode(bom.getBomCode());
        row.setNameCn(bom.getNameCn());
        row.setUniversal(Integer.valueOf(1).equals(bom.getUniversal()) ? "是" : "否");
        row.setSupportModels(bom.getSupportModels());
        row.setSpec(bom.getSpec());
        row.setQty(bom.getQty() == null ? null : bom.getQty().stripTrailingZeros().toPlainString());
        row.setSupplier(bom.getSupplier());
        row.setSupplierPhone(bom.getSupplierPhone());
        row.setSupplierContact(bom.getSupplierContact());
        row.setUnitPrice(bom.getUnitPrice() == null
                ? null : bom.getUnitPrice().stripTrailingZeros().toPlainString());
        row.setCurrency(bom.getCurrency());
        row.setRemark(bom.getRemark());
        return row;
    }
}
