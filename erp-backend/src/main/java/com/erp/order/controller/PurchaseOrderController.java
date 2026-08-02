package com.erp.order.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.doc.ExcelTemplateDocumentService;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.order.dto.PurchaseOrderDTO;
import com.erp.order.dto.PoReceiptDTO;
import com.erp.order.dto.PurchaserOption;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * PO 采购订单 - 主表、明细、分页、单据导出。
 */
@Tag(name = "PO 采购订单")
@RestController
@RequestMapping("/order/po")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final ExcelTemplateDocumentService excelTemplateDocumentService;

    public PurchaseOrderController(PurchaseOrderService service,
                                   ExcelTemplateDocumentService excelTemplateDocumentService) {
        this.service = service;
        this.excelTemplateDocumentService = excelTemplateDocumentService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('purchase_order:list')")
    @GetMapping("/page")
    public Result<PageResult<PurchaseOrder>> page(PageQuery query, @org.springframework.web.bind.annotation.RequestParam(required = false) Long purchaserId) {
        return Result.success(PageResult.of(service.pageQuery(query, purchaserId)));
    }

    @Operation(summary = "采购人选项")
    @PreAuthorize("@perm.has('purchase_order:list')")
    @GetMapping("/purchasers")
    public Result<java.util.List<PurchaserOption>> purchasers() {
        return Result.success(service.purchaserOptions());
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('purchase_order:list')")
    @GetMapping("/{id}")
    public Result<PurchaseOrderDTO> get(@PathVariable Long id) {
        return Result.success(service.getDetail(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "PO采购订单", operation = "新建PO")
    @PreAuthorize("@perm.has('purchase_order:create')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurchaseOrderDTO dto) {
        return Result.success(service.savePo(dto));
    }

    @Operation(summary = "修改")
    @OperationLog(module = "PO采购订单", operation = "修改PO")
    @PreAuthorize("@perm.has('purchase_order:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody PurchaseOrderDTO dto) {
        service.updatePo(dto);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "PO采购订单", operation = "删除PO")
    @PreAuthorize("@perm.has('purchase_order:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removePo(id);
        return Result.success();
    }

    @Operation(summary = "到货入库")
    @OperationLog(module = "PO采购订单", operation = "PO到货入库")
    @PreAuthorize("@perm.has('purchase_order:update')")
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id, @RequestBody PoReceiptDTO receipt) {
        service.receive(id, receipt);
        return Result.success();
    }

    @Operation(summary = "确认 PO")
    @OperationLog(module = "PO采购订单", operation = "确认PO")
    @PreAuthorize("@perm.has('purchase_order:update')")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return Result.success();
    }

    @Operation(summary = "生成 PO 采购订单 (Excel 模板)")
    @PreAuthorize("@perm.has('purchase_order:export')")
    @GetMapping("/{id}/document")
    public void document(@PathVariable Long id, HttpServletResponse response) throws IOException {
        excelTemplateDocumentService.writePurchaseOrder(service.getDetail(id), response);
    }
}
