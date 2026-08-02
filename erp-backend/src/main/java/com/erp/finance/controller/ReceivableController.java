package com.erp.finance.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageResult;
import com.erp.finance.dto.CustomerAging;
import com.erp.finance.dto.ReceivableQuery;
import com.erp.finance.dto.ReceivableRow;
import com.erp.finance.dto.ReceivableSummary;
import com.erp.finance.service.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 应收账款 - PI 应收/已收/未收/账龄 + 客户账龄 + 登记收款核销
 */
@Tag(name = "应收账款")
@RestController
@RequestMapping("/finance/receivable")
public class ReceivableController {

    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    @Operation(summary = "应收账款分页")
    @PreAuthorize("@perm.has('finance:receivable')")
    @GetMapping("/page")
    public Result<PageResult<ReceivableRow>> page(ReceivableQuery query) {
        return Result.success(PageResult.of(service.page(query)));
    }

    @Operation(summary = "应收账款汇总")
    @PreAuthorize("@perm.has('finance:receivable')")
    @GetMapping("/summary")
    public Result<List<ReceivableSummary>> summary(ReceivableQuery query) {
        return Result.success(service.summary(query));
    }

    @Operation(summary = "客户账龄分析")
    @PreAuthorize("@perm.has('finance:receivable')")
    @GetMapping("/aging")
    public Result<List<CustomerAging>> aging(@RequestParam(required = false) Integer termDays) {
        return Result.success(service.customerAging(termDays));
    }

    @Operation(summary = "登记收款(核销到 PI)")
    @OperationLog(module = "应收账款", operation = "登记收款")
    @PreAuthorize("@perm.has('finance:create')")
    @PostMapping("/register")
    public Result<Void> register(@RequestParam Long piId,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(defaultValue = "RECEIVABLE") String receiptType,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
                                 @RequestParam(required = false) String remark) {
        service.registerReceipt(piId, amount, receiptType, entryDate, remark);
        return Result.success();
    }
}
