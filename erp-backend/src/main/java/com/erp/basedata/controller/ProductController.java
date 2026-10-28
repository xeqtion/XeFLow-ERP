package com.erp.basedata.controller;

import com.erp.basedata.dto.ProductExcel;
import com.erp.basedata.entity.Product;
import com.erp.basedata.service.ProductService;
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
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品资料 - CRUD + 分页 + Excel 导入导出
 */
@Tag(name = "产品资料")
@RestController
@RequestMapping("/basedata/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('product:list')")
    @GetMapping("/page")
    public Result<PageResult<Product>> page(PageQuery query) {
        return Result.success(PageResult.of(productService.pageQuery(query)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('product:list')")
    @GetMapping("/{id}")
    public Result<Product> get(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "产品资料", operation = "新建产品")
    @PreAuthorize("@perm.has('product:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody Product product) {
        productService.createProduct(product);
        return Result.id(product.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "产品资料", operation = "修改产品")
    @PreAuthorize("@perm.has('product:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody Product product) {
        if (product.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        productService.updateProduct(product);
        return Result.success();
    }

    @Operation(summary = "修改(允许变更产品ID)")
    @OperationLog(module = "产品资料", operation = "修改产品ID/资料")
    @PreAuthorize("@perm.has('product:update')")
    @PutMapping("/{id}")
    public Result<Void> updateByOriginalId(@PathVariable Long id, @Valid @RequestBody Product product) {
        productService.updateProduct(id, product);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "产品资料", operation = "删除产品")
    @PreAuthorize("@perm.has('product:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @OperationLog(module = "产品资料", operation = "导入产品")
    @PreAuthorize("@perm.has('product:import')")
    @PostMapping("/import")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        return Result.success(productService.importExcel(file));
    }

    @Operation(summary = "Excel 导出 (按关键字)")
    @PreAuthorize("@perm.has('product:export')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<ProductExcel> data = productService.listByKeyword(keyword).stream()
                .map(p -> {
                    ProductExcel e = new ProductExcel();
                    BeanUtils.copyProperties(p, e);
                    e.setId(p.getId() != null ? String.valueOf(p.getId()) : "");
                    return e;
                }).collect(Collectors.toList());
        ExcelUtil.export(response, "产品资料", "产品", ProductExcel.class, data);
    }

    @Operation(summary = "下载导入模板")
    @PreAuthorize("@perm.has('product:export')")
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.template(response, "产品资料导入模板", ProductExcel.class);
    }

}
