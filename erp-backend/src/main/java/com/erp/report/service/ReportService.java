package com.erp.report.service;

import com.erp.report.dto.ProfitRow;
import com.erp.report.dto.ReportAmountSource;
import com.erp.report.dto.ReportQuery;
import com.erp.report.dto.ReportRow;
import com.erp.report.dto.SalesPerformanceDetail;
import com.erp.report.dto.SalesmanRow;
import com.erp.report.dto.SalesmanTrendRow;
import com.erp.report.mapper.ReportMapper;
import com.erp.common.security.DataScopeHelper;
import com.erp.finance.service.AccountingCurrencyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.format.DateTimeFormatter;

/**
 * 经营报表服务 - 采购 / 销售 / 利润, 支持 日/周/月/年 维度。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final DataScopeHelper dataScope;
    private final AccountingCurrencyService accountingCurrencyService;

    public ReportService(ReportMapper reportMapper, DataScopeHelper dataScope,
                         AccountingCurrencyService accountingCurrencyService) {
        this.reportMapper = reportMapper;
        this.dataScope = dataScope;
        this.accountingCurrencyService = accountingCurrencyService;
    }

    public List<ReportRow> purchaseReport(ReportQuery query) {
        return aggregate(reportMapper.purchaseSources(query.getStartDate(), query.getEndDate()), query.getDimension());
    }

    public List<ReportRow> salesReport(ReportQuery query) {
        return aggregate(reportMapper.salesSources(query.getStartDate(), query.getEndDate(),
                dataScope.restrictOwnerId()), query.getDimension());
    }

    /** 按业务员(担当)汇总销售 */
    public List<SalesmanRow> salesBySalesman(ReportQuery query) {
        Map<String, SalesmanRow> rows = new LinkedHashMap<>();
        for (ReportAmountSource source : reportMapper.salesmanSources(query.getStartDate(), query.getEndDate())) {
            String key = String.valueOf(source.getOwnerId()) + "\u0000" + source.getSalesman();
            SalesmanRow row = rows.computeIfAbsent(key, ignored -> {
                SalesmanRow value = new SalesmanRow();
                value.setOwnerId(source.getOwnerId());
                value.setSalesman(source.getSalesman());
                value.setOrderCount(0L);
                value.setTotalAmount(BigDecimal.ZERO);
                value.setCurrency(accountingCurrencyService.baseCurrency());
                return value;
            });
            row.setOrderCount(row.getOrderCount() + 1);
            row.setTotalAmount(row.getTotalAmount().add(toBase(source)));
        }
        BigDecimal totalRevenue = rows.values().stream()
                .map(SalesmanRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        rows.values().forEach(row -> row.setRevenueShare(totalRevenue.signum() == 0
                ? BigDecimal.ZERO
                : row.getTotalAmount().multiply(BigDecimal.valueOf(100))
                .divide(totalRevenue, 2, RoundingMode.HALF_UP)));
        return rows.values().stream()
                .sorted(Comparator.comparing(SalesmanRow::getTotalAmount).reversed())
                .toList();
    }

    /** 按季度或年度返回每位业务员的业绩趋势。 */
    public List<SalesmanTrendRow> salesTrendBySalesman(ReportQuery query) {
        String dimension = "YEAR".equalsIgnoreCase(query.getDimension()) ? "YEAR" : "QUARTER";
        Map<String, SalesmanTrendRow> rows = new TreeMap<>();
        for (ReportAmountSource source : reportMapper.salesmanSources(query.getStartDate(), query.getEndDate())) {
            if (source.getBusinessDate() == null) {
                continue;
            }
            String reportPeriod = period(source.getBusinessDate(), dimension);
            String key = reportPeriod + "\u0000" + String.valueOf(source.getOwnerId()) + "\u0000" + source.getSalesman();
            SalesmanTrendRow row = rows.computeIfAbsent(key, ignored -> {
                SalesmanTrendRow value = new SalesmanTrendRow();
                value.setPeriod(reportPeriod);
                value.setOwnerId(source.getOwnerId());
                value.setSalesman(source.getSalesman());
                value.setOrderCount(0L);
                value.setTotalAmount(BigDecimal.ZERO);
                value.setCurrency(accountingCurrencyService.baseCurrency());
                return value;
            });
            row.setOrderCount(row.getOrderCount() + 1);
            row.setTotalAmount(row.getTotalAmount().add(toBase(source)));
        }
        return new ArrayList<>(rows.values());
    }

    /** 返回业务员业绩所对应的有效 PI，可用于页面下钻核对。 */
    public List<SalesPerformanceDetail> salesPerformanceDetails(Long ownerId, ReportQuery query) {
        List<SalesPerformanceDetail> details = reportMapper.salesmanDetails(
                ownerId, query.getStartDate(), query.getEndDate());
        details.forEach(detail -> detail.setAmountCny(accountingCurrencyService.toBase(
                detail.getOriginalAmount(), detail.getCurrency(), detail.getSignDate())));
        return details;
    }

    /** 利润 = 销售 - 采购, 按周期合并 */
    public List<ProfitRow> profitReport(ReportQuery query) {
        Map<String, BigDecimal> salesMap = toMap(salesReport(query));
        Map<String, BigDecimal> purchaseMap = toMap(purchaseReport(query));

        TreeMap<String, ProfitRow> merged = new TreeMap<>();
        salesMap.forEach((period, sales) -> merged
                .computeIfAbsent(period, ReportService::newRow).setSales(sales));
        purchaseMap.forEach((period, purchase) -> merged
                .computeIfAbsent(period, ReportService::newRow).setPurchase(purchase));

        List<ProfitRow> rows = new ArrayList<>(merged.values());
        for (ProfitRow row : rows) {
            BigDecimal sales = row.getSales() != null ? row.getSales() : BigDecimal.ZERO;
            BigDecimal purchase = row.getPurchase() != null ? row.getPurchase() : BigDecimal.ZERO;
            row.setSales(sales);
            row.setPurchase(purchase);
            row.setProfit(sales.subtract(purchase));
            row.setCurrency(accountingCurrencyService.baseCurrency());
        }
        return rows;
    }

    private static ProfitRow newRow(String period) {
        ProfitRow row = new ProfitRow();
        row.setPeriod(period);
        return row;
    }

    private Map<String, BigDecimal> toMap(List<ReportRow> rows) {
        Map<String, BigDecimal> map = new TreeMap<>();
        for (ReportRow r : rows) {
            map.put(r.getPeriod(), r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO);
        }
        return map;
    }

    private List<ReportRow> aggregate(List<ReportAmountSource> sources, String dimension) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (ReportAmountSource source : sources) {
            if (source.getBusinessDate() == null) {
                continue;
            }
            totals.merge(period(source.getBusinessDate(), dimension), toBase(source), BigDecimal::add);
        }
        return totals.entrySet().stream().map(entry -> {
            ReportRow row = new ReportRow();
            row.setPeriod(entry.getKey());
            row.setAmount(entry.getValue());
            row.setCurrency(accountingCurrencyService.baseCurrency());
            return row;
        }).toList();
    }

    private BigDecimal toBase(ReportAmountSource source) {
        return accountingCurrencyService.toBase(
                source.getAmount(), source.getCurrency(), source.getBusinessDate());
    }

    private String period(LocalDate date, String dimension) {
        String value = dimension == null ? "MONTH" : dimension.toUpperCase();
        return switch (value) {
            case "DAY" -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "WEEK" -> String.format("%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR),
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "QUARTER" -> String.format("%d-Q%d", date.getYear(), ((date.getMonthValue() - 1) / 3) + 1);
            case "YEAR" -> String.valueOf(date.getYear());
            default -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        };
    }
}
