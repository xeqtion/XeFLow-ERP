package com.erp.notification.service;

import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.dto.FinanceSummary;
import com.erp.finance.service.FinanceEntryService;
import com.erp.notification.config.DigestProperties;
import com.erp.notification.constant.NotificationType;
import com.erp.report.dto.ProfitRow;
import com.erp.report.dto.ReportQuery;
import com.erp.report.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 经营摘要服务 - 复用报表聚合, 生成周报/月报 HTML, 经站内通知广播并可选发邮件。
 * 定时任务: 每周一 / 每月1日 自动发送 (cron 可配)。
 */
@Slf4j
@Service
public class DigestService {

    public enum Period { WEEKLY, MONTHLY }

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> WEEKLY_RECIPIENT_ROLES = Set.of("finance", "admin", "super_admin");

    private final ReportService reportService;
    private final FinanceEntryService financeService;
    private final NotificationService notificationService;
    private final DigestProperties props;

    public DigestService(ReportService reportService, FinanceEntryService financeService,
                         NotificationService notificationService, DigestProperties props) {
        this.reportService = reportService;
        this.financeService = financeService;
        this.notificationService = notificationService;
        this.props = props;
    }

    @Scheduled(cron = "${erp.digest.weekly-cron:0 0 8 ? * MON}")
    public void weeklyJob() {
        if (!props.isEnabled()) {
            return;
        }
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(6);
        log.info("[DIGEST] 定时周报触发 {} ~ {}", start, end);
        send(Period.WEEKLY, start, end);
    }

    @Scheduled(cron = "${erp.digest.monthly-cron:0 0 8 1 * ?}")
    public void monthlyJob() {
        if (!props.isEnabled()) {
            return;
        }
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate start = firstOfThisMonth.minusMonths(1);
        LocalDate end = firstOfThisMonth.minusDays(1);
        log.info("[DIGEST] 定时月报触发 {} ~ {}", start, end);
        send(Period.MONTHLY, start, end);
    }

    /** 默认周期: 周报=昨天起前7天; 月报=上一自然月 */
    public LocalDate[] defaultRange(Period period) {
        if (period == Period.MONTHLY) {
            LocalDate first = LocalDate.now().withDayOfMonth(1);
            return new LocalDate[]{first.minusMonths(1), first.minusDays(1)};
        }
        LocalDate end = LocalDate.now().minusDays(1);
        return new LocalDate[]{end.minusDays(6), end};
    }

    /** 生成 + 广播 (站内信 + 可选邮件), 返回接收人数 */
    public int send(Period period, LocalDate start, LocalDate end) {
        String title = title(period, start, end);
        String html = buildHtml(period, start, end);
        if (period == Period.WEEKLY) {
            return notificationService.broadcastToRoles(WEEKLY_RECIPIENT_ROLES,
                    title, html, NotificationType.REPORT, props.isSendEmail());
        }
        return notificationService.broadcast(title, html, NotificationType.REPORT, props.isSendEmail());
    }

    public String title(Period period, LocalDate start, LocalDate end) {
        return period == Period.WEEKLY ? "经营周报" : "经营月报";
    }

    /** 构建 HTML 正文 (复用销售/采购/利润 + 财务汇总) */
    public String buildHtml(Period period, LocalDate start, LocalDate end) {
        ReportQuery rq = new ReportQuery();
        rq.setDimension(period == Period.WEEKLY ? "DAY" : "WEEK");
        rq.setStartDate(start);
        rq.setEndDate(end);
        List<ProfitRow> rows = reportService.profitReport(rq);

        BigDecimal sales = BigDecimal.ZERO, purchase = BigDecimal.ZERO, profit = BigDecimal.ZERO;
        for (ProfitRow r : rows) {
            sales = sales.add(nz(r.getSales()));
            purchase = purchase.add(nz(r.getPurchase()));
            profit = profit.add(nz(r.getProfit()));
        }

        FinanceEntryQuery fq = new FinanceEntryQuery();
        fq.setStartDate(start);
        fq.setEndDate(end);
        List<FinanceSummary> fin = financeService.summaryInBase(fq);

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<div style=\"font-family:'Microsoft YaHei',Arial,sans-serif;color:#303133;max-width:680px\">");
        sb.append("<h2 style=\"color:#1f3a93;margin:0 0 4px\">").append(title(period, start, end)).append("</h2>");
        sb.append("<p style=\"color:#909399;margin:0 0 16px;font-size:13px\">统计区间 ")
                .append(start.format(DF)).append(" 至 ").append(end.format(DF)).append("</p>");

        // 核心指标卡
        sb.append("<table style=\"width:100%;border-collapse:collapse;margin-bottom:16px\"><tr>");
        sb.append(card("销售额 (CNY)", sales, "#2c82c9"));
        sb.append(card("采购额 (CNY)", purchase, "#e6a23c"));
        sb.append(card("利润 (CNY)", profit, profit.signum() >= 0 ? "#67c23a" : "#f56c6c"));
        sb.append("</tr></table>");

        // 周期明细
        sb.append("<h3 style=\"margin:0 0 8px;font-size:15px\">周期明细</h3>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;margin-bottom:16px\">");
        sb.append(th3("周期", "销售额(CNY)", "采购额(CNY)", "利润(CNY)"));
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan=\"4\" style=\"padding:8px;border:1px solid #ebeef5;color:#909399\">区间内暂无数据</td></tr>");
        } else {
            for (ProfitRow r : rows) {
                sb.append("<tr>")
                        .append(td(r.getPeriod(), false))
                        .append(td(money(r.getSales()), true))
                        .append(td(money(r.getPurchase()), true))
                        .append(td(money(r.getProfit()), true))
                        .append("</tr>");
            }
        }
        sb.append("</table>");

        // 财务收付款汇总
        sb.append("<h3 style=\"margin:0 0 8px;font-size:15px\">收付款汇总</h3>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px\">");
        sb.append(th3("类型", "方向", "金额(CNY)", "笔数"));
        if (fin == null || fin.isEmpty()) {
            sb.append("<tr><td colspan=\"4\" style=\"padding:8px;border:1px solid #ebeef5;color:#909399\">区间内暂无收付款</td></tr>");
        } else {
            for (FinanceSummary s : fin) {
                sb.append("<tr>")
                        .append(td(s.getLabel(), false))
                        .append(td("in".equals(s.getDirection()) ? "收" : "付", false))
                        .append(td(money(s.getTotalAmount()), true))
                        .append(td(String.valueOf(s.getCount()), true))
                        .append("</tr>");
            }
        }
        sb.append("</table>");

        sb.append("<p style=\"color:#c0c4cc;margin:16px 0 0;font-size:12px\">本报告由系统自动生成，金额已按单据或入账日期汇率折算为 CNY。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String card(String label, BigDecimal value, String color) {
        return "<td style=\"width:33%;padding:6px\">"
                + "<div style=\"border:1px solid #ebeef5;border-radius:8px;padding:12px;text-align:center\">"
                + "<div style=\"font-size:13px;color:#909399\">" + label + "</div>"
                + "<div style=\"font-size:20px;font-weight:700;color:" + color + ";margin-top:6px\">" + money(value) + "</div>"
                + "</div></td>";
    }

    private String th3(String a, String b, String c, String d) {
        String s = "background:#f5f7fa;padding:8px;border:1px solid #ebeef5;text-align:left";
        return "<tr><th style=\"" + s + "\">" + a + "</th><th style=\"" + s + "\">" + b
                + "</th><th style=\"" + s + "\">" + c + "</th><th style=\"" + s + "\">" + d + "</th></tr>";
    }

    private String td(String text, boolean right) {
        return "<td style=\"padding:8px;border:1px solid #ebeef5;text-align:" + (right ? "right" : "left") + "\">"
                + (text == null ? "" : text) + "</td>";
    }

    private String money(BigDecimal v) {
        return String.format("%,.2f", nz(v));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
