package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataAccessGuard;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.dto.PayableQuery;
import com.erp.finance.dto.PayableRow;
import com.erp.finance.dto.PayableSummary;
import com.erp.finance.dto.SupplierAging;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.mapper.PurchaseOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应付账款 - 把采购 PO(应付) 与付款流水(已付)联动, 计算每张 PO 的已付/未付/账龄/状态。
 * 已付 = 关联本 PO 单号(related_doc_no=poNo)的付款(direction=out)合计。AP 与 AR 对称。
 */
@Slf4j
@Service
public class PayableService {

    private static final Set<String> SUMMARY_CURRENCIES = Set.of("CNY", "USD");

    private final PurchaseOrderMapper poMapper;
    private final FinanceEntryMapper financeMapper;
    private final FinanceEntryService financeEntryService;
    private final AccountingCurrencyService accountingCurrencyService;
    private final DataAccessGuard accessGuard;

    public PayableService(PurchaseOrderMapper poMapper, FinanceEntryMapper financeMapper, FinanceEntryService financeEntryService,
                          AccountingCurrencyService accountingCurrencyService, DataAccessGuard accessGuard) {
        this.poMapper = poMapper;
        this.financeMapper = financeMapper;
        this.financeEntryService = financeEntryService;
        this.accountingCurrencyService = accountingCurrencyService;
        this.accessGuard = accessGuard;
    }

    public IPage<PayableRow> page(PayableQuery query) {
        List<PayableRow> all = buildRows(query);
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.max(1, query.getPageSize());
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        Page<PayableRow> page = new Page<>(pageNum, pageSize, all.size());
        page.setRecords(all.subList(from, to));
        return page;
    }

    public List<PayableSummary> summary(PayableQuery query) {
        List<PayableRow> rows = buildRows(query);
        Map<String, PayableSummary> summaries = new LinkedHashMap<>();
        for (PayableRow r : rows) {
            String currency = normalizedCurrency(r.getCurrency());
            if (!SUMMARY_CURRENCIES.contains(currency)) {
                continue;
            }
            PayableSummary s = summaries.computeIfAbsent(currency, key -> {
                PayableSummary value = new PayableSummary();
                value.setCurrency(key);
                return value;
            });
            s.setPoCount(s.getPoCount() + 1);
            s.setTotalPayable(s.getTotalPayable().add(zero(r.getTotalAmount())));
            s.setTotalPaid(s.getTotalPaid().add(zero(r.getPaidAmount())));
            s.setTotalBalance(s.getTotalBalance().add(zero(r.getBalance()).max(BigDecimal.ZERO)));
            if (r.getBalance().signum() > 0) {
                s.setOpenCount(s.getOpenCount() + 1);
                if (Boolean.TRUE.equals(r.getOverdue())) {
                    s.setOverdueBalance(s.getOverdueBalance().add(r.getBalance()));
                }
            }
        }
        return new ArrayList<>(summaries.values());
    }

    public List<SupplierAging> supplierAging(Integer termDays) {
        PayableQuery q = new PayableQuery();
        q.setTermDays(termDays);
        List<PayableRow> rows = buildRows(q);
        Map<String, SupplierAging> map = new LinkedHashMap<>();
        for (PayableRow r : rows) {
            if (r.getBalance() == null || r.getBalance().signum() <= 0) {
                continue;
            }
            String sup = StringUtils.hasText(r.getSupplier()) ? r.getSupplier() : "(未填供应商)";
            String currency = normalizedCurrency(r.getCurrency());
            String key = sup + "|" + currency;
            SupplierAging a = map.computeIfAbsent(key, k -> {
                SupplierAging x = new SupplierAging();
                x.setSupplier(sup);
                x.setCurrency(currency);
                return x;
            });
            BigDecimal bal = r.getBalance();
            int age = r.getAgeDays() == null ? 0 : r.getAgeDays();
            if (age <= 30) {
                a.setD0_30(a.getD0_30().add(bal));
            } else if (age <= 60) {
                a.setD31_60(a.getD31_60().add(bal));
            } else if (age <= 90) {
                a.setD61_90(a.getD61_90().add(bal));
            } else {
                a.setD90plus(a.getD90plus().add(bal));
            }
            a.setTotalBalance(a.getTotalBalance().add(bal));
        }
        return new ArrayList<>(map.values());
    }

    /** PO 当前待付余额，与应付账款页面使用同一付款流水口径。 */
    public BigDecimal remainingBalance(PurchaseOrder po) {
        if (po == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
        if (!StringUtils.hasText(po.getPoNo())) {
            return total;
        }
        BigDecimal paid = paidByPoNo(List.of(po.getPoNo()))
                .getOrDefault(po.getPoNo(), PaymentTotals.ZERO).original();
        return total.subtract(paid);
    }

    /** 已完成 PO 删除前必须结清应付账款。 */
    public void assertSettledBeforeDelete(PurchaseOrder po) {
        BigDecimal balance = remainingBalance(po);
        if (balance.signum() > 0) {
            String currency = StringUtils.hasText(po.getCurrency()) ? po.getCurrency() : "CNY";
            throw new BusinessException("PO " + po.getPoNo() + " 尚有待付账款 "
                    + balance.stripTrailingZeros().toPlainString() + " " + currency + "，请结清后再删除");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void registerPayment(Long poId, BigDecimal amount, LocalDate entryDate, String remark) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("付款金额必须大于 0");
        }
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) {
            throw new BusinessException("PO 不存在");
        }
        accessGuard.assertRecordVisible(po.getPiId(), null, po.getCreateBy(), "PO采购订单");
        if (!isPayablePo(po)) {
            throw new BusinessException("请先确认 PO 后再登记付款");
        }
        FinanceEntry e = new FinanceEntry();
        e.setType(FinanceType.PAYABLE.name());
        e.setDirection("out");
        e.setAmount(amount);
        e.setCurrency(StringUtils.hasText(po.getCurrency()) ? po.getCurrency() : "CNY");
        e.setRelatedDocType("PO");
        e.setRelatedDocNo(po.getPoNo());
        e.setEntryDate(entryDate != null ? entryDate : LocalDate.now());
        e.setRemark(StringUtils.hasText(remark) ? remark
                : "PO " + po.getPoNo() + " 付款核销" + (StringUtils.hasText(po.getSupplier()) ? " / " + po.getSupplier() : ""));
        financeEntryService.save(e);
        log.info("[AP] 登记付款 PO={} 金额={} {}", po.getPoNo(), amount, e.getCurrency());
    }

    // ---------------- 私有 ----------------

    private List<PayableRow> buildRows(PayableQuery query) {
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getStatus, "CONFIRMED", "IN_PROGRESS", "COMPLETED", "RECEIVED")
                .like(StringUtils.hasText(query.getSupplier()), PurchaseOrder::getSupplier, query.getSupplier())
                .like(StringUtils.hasText(query.getKeyword()), PurchaseOrder::getPoNo, query.getKeyword())
                .orderByDesc(PurchaseOrder::getOrderDate, PurchaseOrder::getId);
        List<PurchaseOrder> pos = poMapper.selectList(w);
        if (pos.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> poNos = pos.stream().map(PurchaseOrder::getPoNo).filter(StringUtils::hasText).toList();
        Map<String, PaymentTotals> paid = paidByPoNo(poNos);
        int term = query.getTermDays() != null ? query.getTermDays() : 30;
        LocalDate today = LocalDate.now();

        List<PayableRow> rows = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            BigDecimal total = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
            PaymentTotals payment = paid.getOrDefault(po.getPoNo(), PaymentTotals.ZERO);
            BigDecimal got = payment.original();
            BigDecimal bal = total.subtract(got);
            BigDecimal totalBase = accountingCurrencyService.toBase(total, po.getCurrency(), po.getOrderDate());
            BigDecimal balanceBase = totalBase.subtract(payment.base()).max(BigDecimal.ZERO);

            PayableRow r = new PayableRow();
            r.setPoId(po.getId());
            r.setPoNo(po.getPoNo());
            r.setSupplier(po.getSupplier());
            r.setCurrency(po.getCurrency());
            r.setBaseCurrency(accountingCurrencyService.baseCurrency());
            r.setOrderDate(po.getOrderDate());
            r.setTotalAmount(total);
            r.setTotalAmountBase(totalBase);
            r.setPaidAmount(got);
            r.setPaidAmountBase(payment.base());
            r.setBalance(bal);
            r.setBalanceBase(balanceBase);
            r.setSettleStatus(bal.signum() <= 0 ? "PAID" : (got.signum() > 0 ? "PARTIAL" : "UNPAID"));
            Integer age = po.getOrderDate() != null
                    ? (int) ChronoUnit.DAYS.between(po.getOrderDate(), today) : null;
            r.setAgeDays(age);
            r.setOverdue(bal.signum() > 0 && age != null && age > term);
            rows.add(r);
        }
        String status = query.getStatus();
        boolean overdueOnly = Boolean.TRUE.equals(query.getOverdueOnly());
        return rows.stream()
                .filter(r -> !StringUtils.hasText(status) || status.equals(r.getSettleStatus()))
                .filter(r -> !overdueOnly || Boolean.TRUE.equals(r.getOverdue()))
                .toList();
    }

    private Map<String, PaymentTotals> paidByPoNo(List<String> poNos) {
        Map<String, PaymentTotals> map = new HashMap<>();
        if (poNos == null || poNos.isEmpty()) {
            return map;
        }
        List<FinanceEntry> entries = financeMapper.selectList(new LambdaQueryWrapper<FinanceEntry>()
                .eq(FinanceEntry::getDirection, "out")
                .in(FinanceEntry::getRelatedDocNo, poNos));
        for (FinanceEntry entry : entries) {
            if (!StringUtils.hasText(entry.getRelatedDocNo()) || entry.getAmount() == null) {
                continue;
            }
            BigDecimal base = accountingCurrencyService.toBase(
                    entry.getAmount(), entry.getCurrency(), entry.getEntryDate());
            map.merge(entry.getRelatedDocNo(), new PaymentTotals(entry.getAmount(), base), PaymentTotals::add);
        }
        return map;
    }

    private boolean isPayablePo(PurchaseOrder po) {
        return po != null && java.util.Set.of("CONFIRMED", "IN_PROGRESS", "COMPLETED", "RECEIVED")
                .contains(po.getStatus());
    }

    private record PaymentTotals(BigDecimal original, BigDecimal base) {
        private static final PaymentTotals ZERO = new PaymentTotals(BigDecimal.ZERO, BigDecimal.ZERO);

        private PaymentTotals add(PaymentTotals other) {
            return new PaymentTotals(original.add(other.original), base.add(other.base));
        }
    }

    private String normalizedCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "CNY";
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
