package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.entity.CurrencyRate;
import com.erp.basedata.dto.CurrencyOption;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.FxOnlineService;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 汇率管理 - CRUD + 本位币换算
 */
@Tag(name = "汇率管理")
@RestController
@RequestMapping("/basedata/currency")
public class CurrencyController {

    private final CurrencyService currencyService;
    private final FxOnlineService fxOnlineService;

    public CurrencyController(CurrencyService currencyService, FxOnlineService fxOnlineService) {
        this.currencyService = currencyService;
        this.fxOnlineService = fxOnlineService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('currency:list')")
    @GetMapping("/page")
    public Result<PageResult<CurrencyRate>> page(
            PageQuery query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(PageResult.of(currencyService.pageQuery(query, date)));
    }

    @Operation(summary = "全部汇率")
    @PreAuthorize("@perm.has('currency:list')")
    @GetMapping("/list")
    public Result<List<CurrencyRate>> list() {
        return Result.success(currencyService.list(
                Wrappers.<CurrencyRate>lambdaQuery().orderByDesc(CurrencyRate::getEffectiveDate)));
    }

    @Operation(summary = "本位币代码")
    @GetMapping("/base")
    public Result<Map<String, String>> base() {
        return Result.success(Map.of("base", currencyService.baseCurrency()));
    }

    @Operation(summary = "支持的币种")
    @PreAuthorize("@perm.has('currency:list')")
    @GetMapping("/currencies")
    public Result<List<CurrencyOption>> currencies() {
        return Result.success(fxOnlineService.currencyOptions());
    }

    @Operation(summary = "新建汇率")
    @OperationLog(module = "汇率管理", operation = "新建汇率")
    @PreAuthorize("@perm.has('currency:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody CurrencyRate rate) {
        normalize(rate);
        rate.setId(null);
        currencyService.save(rate);
        return Result.id(rate.getId());
    }

    @Operation(summary = "修改汇率")
    @OperationLog(module = "汇率管理", operation = "修改汇率")
    @PreAuthorize("@perm.has('currency:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody CurrencyRate rate) {
        if (rate.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        normalize(rate);
        currencyService.updateById(rate);
        return Result.success();
    }

    @Operation(summary = "删除汇率")
    @OperationLog(module = "汇率管理", operation = "删除汇率")
    @PreAuthorize("@perm.has('currency:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        currencyService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "拉取在线实时汇率(写入今日)")
    @OperationLog(module = "汇率管理", operation = "拉取实时汇率")
    @PreAuthorize("@perm.has('currency:update')")
    @PostMapping("/refresh-online")
    public Result<Map<String, Object>> refreshOnline() {
        return Result.success("已更新", fxOnlineService.refresh());
    }

    @Operation(summary = "币种换算")
    @PreAuthorize("@perm.has('currency:list')")
    @GetMapping("/convert")
    public Result<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String sourceCurrency = StringUtils.hasText(from) ? from : currency;
        if (!StringUtils.hasText(sourceCurrency)) {
            throw new BusinessException("请选择源币种");
        }
        String targetCurrency = StringUtils.hasText(to) ? to : currencyService.baseCurrency();
        LocalDate conversionDate = date == null ? LocalDate.now() : date;
        BigDecimal fromRate = currencyService.rate(sourceCurrency, conversionDate);
        BigDecimal toRate = currencyService.rate(targetCurrency, conversionDate);
        BigDecimal convertedAmount = currencyService.convert(
                amount, sourceCurrency, targetCurrency, conversionDate);
        BigDecimal crossRate = fromRate.divide(toRate, 8, java.math.RoundingMode.HALF_UP);
        return Result.success(Map.of(
                "base", currencyService.baseCurrency(),
                "date", conversionDate.toString(),
                "from", sourceCurrency.trim().toUpperCase(),
                "to", targetCurrency.trim().toUpperCase(),
                "rate", crossRate,
                "fromRateToBase", fromRate,
                "toRateToBase", toRate,
                "convertedAmount", convertedAmount,
                "baseAmount", convertedAmount));
    }

    private void normalize(CurrencyRate rate) {
        if (rate.getCurrency() != null) {
            rate.setCurrency(rate.getCurrency().trim().toUpperCase());
        }
        rate.setCurrencyName(StringUtils.hasText(rate.getCurrencyName())
                ? rate.getCurrencyName().trim()
                : null);
        if (rate.getRateToBase() == null || rate.getRateToBase().signum() <= 0) {
            throw new BusinessException("汇率必须大于 0");
        }
    }
}
