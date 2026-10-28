package com.erp.sales.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageResult;
import com.erp.sales.dto.ContractPriceQuery;
import com.erp.sales.entity.ContractPrice;
import com.erp.sales.service.ContractPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 销售报价 - 录入自动按产品报价区间判定审核状态。
 */
@Tag(name = "销售报价")
@RestController
@RequestMapping("/sales/contract-price")
public class ContractPriceController {

    private final ContractPriceService service;

    public ContractPriceController(ContractPriceService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询 (可按审核状态过滤)")
    @PreAuthorize("@perm.has('contract_price:list')")
    @GetMapping("/page")
    public Result<PageResult<ContractPrice>> page(ContractPriceQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('contract_price:list')")
    @GetMapping("/{id}")
    public Result<ContractPrice> get(@PathVariable Long id) {
        return Result.success(service.getVisible(id));
    }

    @Operation(summary = "新建 (自动判定审核状态)")
    @OperationLog(module = "销售报价", operation = "新建报价")
    @PreAuthorize("@perm.has('contract_price:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody ContractPrice cp) {
        return Result.id(service.createWithCompare(cp));
    }

    @Operation(summary = "修改 (重新判定审核状态)")
    @OperationLog(module = "销售报价", operation = "修改报价")
    @PreAuthorize("@perm.has('contract_price:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ContractPrice cp) {
        service.updateWithCompare(cp);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "销售报价", operation = "删除报价")
    @PreAuthorize("@perm.has('contract_price:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.cancel(id);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @OperationLog(module = "销售报价", operation = "审批通过")
    @PreAuthorize("@perm.has('contract_price:approve')")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @OperationLog(module = "销售报价", operation = "审批驳回")
    @PreAuthorize("@perm.has('contract_price:approve')")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.reject(id, body != null ? body.get("reason") : null);
        return Result.success();
    }

    @Operation(summary = "重新审核")
    @OperationLog(module = "销售报价", operation = "重新审核")
    @PreAuthorize("@perm.has('contract_price:approve')")
    @PostMapping("/{id}/review")
    public Result<Void> requestReview(@PathVariable Long id) {
        service.requestReview(id);
        return Result.success();
    }
}
