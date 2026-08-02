package com.erp.inventory.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.inventory.dto.StockRow;
import com.erp.inventory.entity.StockMovement;
import com.erp.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 库存管理 - 结存查询 + 出入库 + 安全库存预警
 */
@Tag(name = "库存管理")
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final StockService stockService;

    public InventoryController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(summary = "库存结存分页")
    @PreAuthorize("@perm.has('inventory:view')")
    @GetMapping("/stock")
    public Result<PageResult<StockRow>> stock(PageQuery query,
                                              @RequestParam(required = false) Boolean lowOnly) {
        return Result.success(PageResult.of(stockService.pageStock(query, lowOnly)));
    }

    @Operation(summary = "低于安全库存的产品数")
    @PreAuthorize("@perm.has('inventory:view')")
    @GetMapping("/low-count")
    public Result<Map<String, Long>> lowCount() {
        return Result.success(Map.of("low", stockService.lowStockCount()));
    }

    @Operation(summary = "设置产品安全库存")
    @OperationLog(module = "库存管理", operation = "设置安全库存")
    @PreAuthorize("@perm.has('inventory:manage')")
    @PutMapping("/stock/{productId}/safety-stock")
    public Result<Void> updateSafetyStock(@PathVariable Long productId,
                                          @RequestParam BigDecimal safetyStock) {
        stockService.updateSafetyStock(productId, safetyStock);
        return Result.success();
    }

    @Operation(summary = "某产品出入库流水")
    @PreAuthorize("@perm.has('inventory:view')")
    @GetMapping("/movements")
    public Result<PageResult<StockMovement>> movements(@RequestParam String prodCode, PageQuery query) {
        return Result.success(PageResult.of(stockService.movements(prodCode, query)));
    }

    @Operation(summary = "出入库登记")
    @OperationLog(module = "库存管理", operation = "出入库登记")
    @PreAuthorize("@perm.has('inventory:manage')")
    @PostMapping("/movement")
    public Result<Long> record(@RequestParam String prodCode,
                               @RequestParam String direction,
                               @RequestParam BigDecimal qty,
                               @RequestParam(required = false) String bizType,
                               @RequestParam(required = false) String relatedDocNo,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate moveDate,
                               @RequestParam(required = false) String remark) {
        return Result.success("登记成功", stockService.recordMovement(prodCode, direction, qty, bizType, relatedDocNo, moveDate, remark));
    }
}
