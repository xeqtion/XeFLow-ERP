package com.erp.report.controller;

import com.erp.basedata.entity.Customer;
import com.erp.basedata.service.CustomerService;
import com.erp.common.api.Result;
import com.erp.common.doc.WordDocUtil;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.exception.BusinessException;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.service.FinanceEntryService;
import com.erp.finance.service.AccountingCurrencyService;
import com.erp.report.dto.ProfitRow;
import com.erp.report.dto.ReportQuery;
import com.erp.report.dto.ReportRow;
import com.erp.report.dto.SalesPerformanceDetail;
import com.erp.report.dto.SalesmanRow;
import com.erp.report.dto.SalesmanTrendRow;
import com.erp.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 经营报表 - 采购/销售/利润 (日/周/月/年) + 月对账单 / 外贸业绩报告 单据
 */
@Tag(name = "经营报表")
@RestController
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;
    private final FinanceEntryService financeEntryService;
    private final CustomerService customerService;
    private final AccountingCurrencyService accountingCurrencyService;

    public ReportController(ReportService reportService, FinanceEntryService financeEntryService,
                            CustomerService customerService,
                            AccountingCurrencyService accountingCurrencyService) {
        this.reportService = reportService;
        this.financeEntryService = financeEntryService;
        this.customerService = customerService;
        this.accountingCurrencyService = accountingCurrencyService;
    }

    // ---------------- 数据 ----------------

    @Operation(summary = "采购报表")
    @PreAuthorize("@perm.has('report:purchase')")
    @GetMapping("/purchase")
    public Result<List<ReportRow>> purchase(ReportQuery query) {
        return Result.success(reportService.purchaseReport(query));
    }

    @Operation(summary = "销售报表")
    @PreAuthorize("@perm.has('report:sales')")
    @GetMapping("/sales")
    public Result<List<ReportRow>> sales(ReportQuery query) {
        return Result.success(reportService.salesReport(query));
    }

    @Operation(summary = "财务利润报表")
    @PreAuthorize("@perm.has('report:profit')")
    @GetMapping("/profit")
    public Result<List<ProfitRow>> profit(ReportQuery query) {
        return Result.success(reportService.profitReport(query));
    }

    @Operation(summary = "按业务员销售汇总 (仅高权限 data:all)")
    @PreAuthorize("@perm.has('data:all')")
    @GetMapping("/by-salesman")
    public Result<List<SalesmanRow>> bySalesman(ReportQuery query) {
        return Result.success(reportService.salesBySalesman(query));
    }

    @Operation(summary = "业务员季度/年度业绩趋势")
    @PreAuthorize("@perm.has('data:all')")
    @GetMapping("/by-salesman/trend")
    public Result<List<SalesmanTrendRow>> salesmanTrend(ReportQuery query) {
        return Result.success(reportService.salesTrendBySalesman(query));
    }

    @Operation(summary = "业务员业绩来源 PI 明细")
    @PreAuthorize("@perm.has('data:all')")
    @GetMapping("/by-salesman/details")
    public Result<List<SalesPerformanceDetail>> salesmanDetails(
            @RequestParam Long ownerId, ReportQuery query) {
        return Result.success(reportService.salesPerformanceDetails(ownerId, query));
    }

    // ---------------- Excel 导出 ----------------

    @Operation(summary = "采购报表导出")
    @PreAuthorize("@perm.has('report:purchase')")
    @GetMapping("/purchase/export")
    public void purchaseExport(ReportQuery query, HttpServletResponse response) throws IOException {
        ExcelUtil.export(response, "采购报表", "采购", ReportRow.class, reportService.purchaseReport(query));
    }

    @Operation(summary = "销售报表导出")
    @PreAuthorize("@perm.has('report:sales')")
    @GetMapping("/sales/export")
    public void salesExport(ReportQuery query, HttpServletResponse response) throws IOException {
        ExcelUtil.export(response, "销售报表", "销售", ReportRow.class, reportService.salesReport(query));
    }

    @Operation(summary = "利润报表导出")
    @PreAuthorize("@perm.has('report:profit')")
    @GetMapping("/profit/export")
    public void profitExport(ReportQuery query, HttpServletResponse response) throws IOException {
        ExcelUtil.export(response, "财务利润报表", "利润", ProfitRow.class, reportService.profitReport(query));
    }

    // ---------------- 单据 ----------------

    @Operation(summary = "销售月对账单 (Word)")
    @PreAuthorize("@perm.has('report:profit')")
    @GetMapping("/statement")
    public void statement(@RequestParam(required = false) String customerCode,
                          @RequestParam(required = false) Long customerId,
                          @RequestParam String month,
                          HttpServletResponse response) throws IOException {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        Customer customer = null;
        if (StringUtils.hasText(customerCode)) {
            customer = customerService.lambdaQuery().eq(Customer::getCustCode, customerCode.trim()).one();
        } else if (customerId != null) {
            customer = customerService.getById(customerId);
        }
        if (customer == null) {
            throw new BusinessException("客户ID不存在");
        }
        String visibleCustomerId = customer.getCustCode();

        FinanceEntryQuery q = new FinanceEntryQuery();
        q.setCustomerCode(visibleCustomerId);
        q.setStartDate(ym.atDay(1));
        q.setEndDate(ym.atEndOfMonth());
        List<FinanceEntry> entries = financeEntryService.listForExport(q);

        String custName = visibleCustomerId + " / " + nz(customer.getCustNameEn()) + " / " + nz(customer.getCustNameCn());

        XWPFDocument doc = new XWPFDocument();
        WordDocUtil.title(doc, "销售月对账单 / MONTHLY STATEMENT");
        WordDocUtil.line(doc, "客户 / Customer: " + custName);
        WordDocUtil.line(doc, "月份 / Month: " + month);

        XWPFTable table = WordDocUtil.table(doc, "日期 Date", "类型 Type", "方向 In/Out",
                "原币金额 Original", "折算金额 CNY", "单据 Doc");
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (FinanceEntry e : entries) {
            FinanceType t = FinanceType.of(e.getType());
            BigDecimal baseAmount = accountingCurrencyService.toBase(
                    e.getAmount(), e.getCurrency(), e.getEntryDate());
            WordDocUtil.addRow(table,
                    e.getEntryDate() != null ? e.getEntryDate().toString() : "",
                    t != null ? t.getLabel() : nz(e.getType()),
                    "in".equals(e.getDirection()) ? "收 In" : "付 Out",
                    str(e.getAmount()) + " " + nz(e.getCurrency()),
                    str(baseAmount) + " CNY",
                    nz(e.getRelatedDocNo()));
            if ("in".equals(e.getDirection())) {
                received = received.add(baseAmount);
            } else {
                paid = paid.add(baseAmount);
            }
        }
        WordDocUtil.line(doc, "");
        WordDocUtil.line(doc, "收款合计 Total In: " + str(received) + " CNY    付款合计 Total Out: " + str(paid) + " CNY");
        WordDocUtil.line(doc, "净额 Net (In - Out): " + str(received.subtract(paid)) + " CNY");
        WordDocUtil.writeDocx(response, "对账单_" + visibleCustomerId + "_" + month, doc);
    }

    @Operation(summary = "外贸业绩报告 (Word)")
    @PreAuthorize("@perm.has('report:profit')")
    @GetMapping("/performance")
    public void performance(ReportQuery query, HttpServletResponse response) throws IOException {
        query.setDimension("MONTH");
        List<ProfitRow> rows = reportService.profitReport(query);

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalPurchase = BigDecimal.ZERO;
        for (ProfitRow r : rows) {
            totalSales = totalSales.add(r.getSales());
            totalPurchase = totalPurchase.add(r.getPurchase());
        }
        BigDecimal totalProfit = totalSales.subtract(totalPurchase);

        XWPFDocument doc = new XWPFDocument();
        WordDocUtil.title(doc, "外贸业绩报告 / FOREIGN TRADE PERFORMANCE");
        String range = (query.getStartDate() != null ? query.getStartDate().toString() : "—")
                + " ~ " + (query.getEndDate() != null ? query.getEndDate().toString() : "—");
        WordDocUtil.line(doc, "统计区间 / Period: " + range);
        WordDocUtil.line(doc, "销售总额 / Total Sales: " + str(totalSales) + " CNY");
        WordDocUtil.line(doc, "采购总额 / Total Purchase: " + str(totalPurchase) + " CNY");
        WordDocUtil.line(doc, "利润总额 / Total Profit: " + str(totalProfit) + " CNY");
        WordDocUtil.line(doc, "");
        WordDocUtil.line(doc, "按月明细 / Monthly Breakdown:");

        XWPFTable table = WordDocUtil.table(doc, "月份 Month", "销售 Sales(CNY)", "采购 Purchase(CNY)", "利润 Profit(CNY)");
        for (ProfitRow r : rows) {
            WordDocUtil.addRow(table, r.getPeriod(), str(r.getSales()), str(r.getPurchase()), str(r.getProfit()));
        }
        WordDocUtil.writeDocx(response, "外贸业绩报告_" + range, doc);
    }

    private String str(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
