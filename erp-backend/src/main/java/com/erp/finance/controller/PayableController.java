package com.erp.finance.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageResult;
import com.erp.finance.dto.PayableQuery;
import com.erp.finance.dto.PayableRow;
import com.erp.finance.dto.PayableSummary;
import com.erp.finance.dto.SupplierAging;
import com.erp.finance.service.PayableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 应付账款 - PO 应付/已付/未付/账龄 + 供应商账龄 + 登记付款核销
 */
@Tag(name = "应付账款")
@RestController
@RequestMapping("/finance/payable")
public class PayableController {

    private final PayableService service;

    public PayableController(PayableService service) {
        this.service = service;
    }

    @Operation(summary = "应付账款分页")
    @PreAuthorize("@perm.has('finance:payable')")
    @GetMapping("/page")
    public Result<PageResult<PayableRow>> page(PayableQuery query) {
        return Result.success(PageResult.of(service.page(query)));
    }

    @Operation(summary = "应付账款汇总")
    @PreAuthorize("@perm.has('finance:payable')")
    @GetMapping("/summary")
    public Result<List<PayableSummary>> summary(PayableQuery query) {
        return Result.success(service.summary(query));
    }

    @Operation(summary = "供应商账龄分析")
    @PreAuthorize("@perm.has('finance:payable')")
    @GetMapping("/aging")
    public Result<List<SupplierAging>> aging(@RequestParam(required = false) Integer termDays) {
        return Result.success(service.supplierAging(termDays));
    }

    @Operation(summary = "登记付款(核销到 PO)")
    @OperationLog(module = "应付账款", operation = "登记付款")
    @PreAuthorize("@perm.has('finance:create')")
    @PostMapping("/register")
    public Result<Void> register(@RequestParam Long poId,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
                                 @RequestParam(required = false) String remark) {
        service.registerPayment(poId, amount, entryDate, remark);
        return Result.success();
    }
}
