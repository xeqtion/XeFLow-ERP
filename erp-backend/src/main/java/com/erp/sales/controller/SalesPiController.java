package com.erp.sales.controller;

import com.erp.basedata.service.CustomerService;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.doc.ExcelTemplateDocumentService;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.sales.dto.SalesPiDTO;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.service.SalesPiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售 PI - 主表、明细、分页、单据导出。
 */
@Tag(name = "销售合同 PI")
@RestController
@RequestMapping("/sales/pi")
public class SalesPiController {

    private final SalesPiService service;
    private final CustomerService customerService;
    private final ExcelTemplateDocumentService excelTemplateDocumentService;

    public SalesPiController(SalesPiService service,
                             CustomerService customerService,
                             ExcelTemplateDocumentService excelTemplateDocumentService) {
        this.service = service;
        this.customerService = customerService;
        this.excelTemplateDocumentService = excelTemplateDocumentService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('sales_pi:list')")
    @GetMapping("/page")
    public Result<PageResult<SalesPi>> page(PageQuery query, @RequestParam(required = false) Long ownerId) {
        return Result.success(PageResult.of(service.pageQuery(query, ownerId)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('sales_pi:list')")
    @GetMapping("/{id}")
    public Result<SalesPiDTO> get(@PathVariable Long id) {
        return Result.success(service.getDetail(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "销售合同PI", operation = "新建PI")
    @PreAuthorize("@perm.has('sales_pi:create')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalesPiDTO dto) {
        return Result.success(service.savePi(dto));
    }

    @Operation(summary = "修改")
    @OperationLog(module = "销售合同PI", operation = "修改PI")
    @PreAuthorize("@perm.has('sales_pi:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SalesPiDTO dto) {
        service.updatePi(dto);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "销售合同PI", operation = "删除PI")
    @PreAuthorize("@perm.has('sales_pi:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removePi(id);
        return Result.success();
    }

    @Operation(summary = "确认 PI")
    @OperationLog(module = "销售合同PI", operation = "确认PI")
    @PreAuthorize("@perm.has('sales_pi:update')")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return Result.success();
    }

    @Operation(summary = "由 PI 生成出货单")
    @OperationLog(module = "销售合同PI", operation = "PI生成出货")
    @PreAuthorize("@perm.has('sales_pi:update')")
    @PostMapping("/{id}/ship")
    public Result<Long> ship(@PathVariable Long id,
                             @RequestParam(required = false) BigDecimal qty,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shipDate) {
        return Result.success("已生成出货单", service.generateShipment(id, qty, shipDate));
    }

    @Operation(summary = "生成 PI 形式发票 (Excel 模板)")
    @PreAuthorize("@perm.has('sales_pi:export')")
    @GetMapping("/{id}/document")
    public void document(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SalesPiDTO pi = service.getDetail(id);
        excelTemplateDocumentService.writePi(pi, customerService.getById(pi.getCustomerId()), response);
    }

    @Operation(summary = "生成 CI 商业发票 (Excel 模板)")
    @PreAuthorize("@perm.has('sales_pi:export')")
    @GetMapping("/{id}/commercial-invoice")
    public void commercialInvoice(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SalesPiDTO pi = service.getDetail(id);
        excelTemplateDocumentService.writeCommercialInvoice(pi, customerService.getById(pi.getCustomerId()), response);
    }
}
