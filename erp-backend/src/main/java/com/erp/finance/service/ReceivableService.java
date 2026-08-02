package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataAccessGuard;
import com.erp.common.security.DataScopeHelper;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.dto.CustomerAging;
import com.erp.finance.dto.ReceivableQuery;
import com.erp.finance.dto.ReceivableRow;
import com.erp.finance.dto.ReceivableSummary;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
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
 * 应收账款 - 把销售 PI(应收) 与收款流水(已收)联动, 计算每张 PI 的已收/未收/账龄/结算状态,
 * 提供客户账龄分析与"登记收款核销"。已收 = 关联本 PI 单号(related_doc_no=piNo)的收款(direction=in)合计。
 */
@Slf4j
@Service
public class ReceivableService {

    private static final Set<String> SUMMARY_CURRENCIES = Set.of("CNY", "USD");

    private final SalesPiMapper piMapper;
    private final FinanceEntryMapper financeMapper;
    private final FinanceEntryService financeEntryService;
    private final CustomerMapper customerMapper;
    private final AccountingCurrencyService accountingCurrencyService;
    private final DataScopeHelper dataScope;
    private final DataAccessGuard accessGuard;
    private final PiPaymentService piPaymentService;

    public ReceivableService(SalesPiMapper piMapper, FinanceEntryMapper financeMapper,
                             FinanceEntryService financeEntryService, CustomerMapper customerMapper,
                             AccountingCurrencyService accountingCurrencyService,
                             DataScopeHelper dataScope, DataAccessGuard accessGuard,
                             PiPaymentService piPaymentService) {
        this.piMapper = piMapper;
        this.financeMapper = financeMapper;
        this.financeEntryService = financeEntryService;
        this.customerMapper = customerMapper;
        this.accountingCurrencyService = accountingCurrencyService;
        this.dataScope = dataScope;
        this.accessGuard = accessGuard;
        this.piPaymentService = piPaymentService;
    }

    /** 分页应收账款(按计算出的状态过滤后内存分页) */
    public IPage<ReceivableRow> page(ReceivableQuery query) {
        List<ReceivableRow> all = buildRows(query);
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.max(1, query.getPageSize());
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        Page<ReceivableRow> page = new Page<>(pageNum, pageSize, all.size());
        page.setRecords(all.subList(from, to));
        return page;
    }

    /** 当前过滤条件下按单据原币分别汇总，不产生汇兑差额形成的虚假余额。 */
    public List<ReceivableSummary> summary(ReceivableQuery query) {
        List<ReceivableRow> rows = buildRows(query);
        Map<String, ReceivableSummary> summaries = new LinkedHashMap<>();
        for (ReceivableRow r : rows) {
            String currency = normalizedCurrency(r.getCurrency());
            if (!SUMMARY_CURRENCIES.contains(currency)) {
                continue;
            }
            ReceivableSummary s = summaries.computeIfAbsent(currency, key -> {
                ReceivableSummary value = new ReceivableSummary();
                value.setCurrency(key);
                return value;
            });
            s.setPiCount(s.getPiCount() + 1);
            s.setTotalReceivable(s.getTotalReceivable().add(zero(r.getTotalAmount())));
            s.setTotalReceived(s.getTotalReceived().add(zero(r.getReceivedAmount())));
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

    /** 客户账龄按单据原币分别统计，仅含原币余额大于零的单据。 */
    public List<CustomerAging> customerAging(Integer termDays) {
        ReceivableQuery q = new ReceivableQuery();
        q.setTermDays(termDays);
        List<ReceivableRow> rows = buildRows(q);
        Map<String, CustomerAging> map = new LinkedHashMap<>();
        for (ReceivableRow r : rows) {
            if (r.getBalance() == null || r.getBalance().signum() <= 0) {
                continue;
            }
            String currency = normalizedCurrency(r.getCurrency());
            String key = String.valueOf(r.getCustomerId()) + "|" + currency;
            CustomerAging a = map.computeIfAbsent(key, k -> {
                CustomerAging x = new CustomerAging();
                x.setCustomerId(r.getCustomerId());
                x.setCustomerCode(r.getCustomerCode());
                x.setCustomerName(r.getCustomerName());
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

    /** 登记一笔收款核销到指定 PI(创建 direction=in 的收款流水, related_doc_no=piNo) */
    @Transactional(rollbackFor = Exception.class)
    public void registerReceipt(Long piId, BigDecimal amount, String receiptType, LocalDate entryDate, String remark) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("收款金额必须大于 0");
        }
        accessGuard.assertPiVisible(piId);
        SalesPi pi = piMapper.selectById(piId);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        if ("DRAFT".equals(pi.getStatus()) || !StringUtils.hasText(pi.getStatus())) {
            throw new BusinessException("PI 尚未确认，不能登记收款");
        }
        String normalizedType = FinanceType.PRE_RECEIVE.name().equals(receiptType)
                ? FinanceType.PRE_RECEIVE.name() : FinanceType.RECEIVABLE.name();
        BigDecimal received = piPaymentService.receivedAmount(pi.getPiNo());
        BigDecimal remaining = (pi.getTotalAmount() == null ? BigDecimal.ZERO : pi.getTotalAmount()).subtract(received);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException("收款金额不能超过 PI 未收余额 " + remaining.stripTrailingZeros().toPlainString());
        }
        if (FinanceType.PRE_RECEIVE.name().equals(normalizedType)) {
            BigDecimal deposit = pi.getDepositAmount() == null ? BigDecimal.ZERO : pi.getDepositAmount();
            BigDecimal depositRemaining = deposit.subtract(piPaymentService.depositReceivedAmount(pi.getPiNo()));
            if (deposit.signum() > 0 && amount.compareTo(depositRemaining) > 0) {
                throw new BusinessException("定金收款不能超过未收定金 " + depositRemaining.stripTrailingZeros().toPlainString());
            }
        }
        FinanceEntry e = new FinanceEntry();
        e.setType(normalizedType);
        e.setDirection("in");
        e.setCustomerId(pi.getCustomerId());
        e.setAmount(amount);
        e.setCurrency(StringUtils.hasText(pi.getCurrency()) ? pi.getCurrency() : "USD");
        e.setRelatedDocType("PI");
        e.setRelatedDocNo(pi.getPiNo());
        e.setEntryDate(entryDate != null ? entryDate : LocalDate.now());
        e.setRemark(StringUtils.hasText(remark) ? remark : "PI " + pi.getPiNo() + " 收款核销");
        financeEntryService.save(e);
        piPaymentService.syncPiAfterReceipt(pi);
        log.info("[AR] 登记收款 PI={} 金额={} {}", pi.getPiNo(), amount, e.getCurrency());
    }

    // ---------------- 私有 ----------------

    /** 构建(并按 status/overdue 过滤)全部应收行 */
    private List<ReceivableRow> buildRows(ReceivableQuery query) {
        Long ownerId = dataScope.restrictOwnerId();
        Long customerId = resolveCustomerId(query.getCustomerCode(), query.getCustomerId());
        LambdaQueryWrapper<SalesPi> w = new LambdaQueryWrapper<SalesPi>()
                .eq(ownerId != null, SalesPi::getOwnerId, ownerId)
                .eq(customerId != null, SalesPi::getCustomerId, customerId)
                .in(SalesPi::getStatus, "CONFIRMED", "ORDERED", "SHIPPED", "COMPLETED", "CLOSED")
                .like(StringUtils.hasText(query.getKeyword()), SalesPi::getPiNo, query.getKeyword())
                .orderByDesc(SalesPi::getSignDate, SalesPi::getId);
        List<SalesPi> pis = piMapper.selectList(w);
        if (pis.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> piNos = pis.stream().map(SalesPi::getPiNo).filter(StringUtils::hasText).toList();
        Map<String, PaymentTotals> received = receivedByPiNo(piNos);
        Map<Long, Customer> customers = customers(pis);
        int term = query.getTermDays() != null ? query.getTermDays() : 30;
        LocalDate today = LocalDate.now();

        List<ReceivableRow> rows = new ArrayList<>();
        for (SalesPi pi : pis) {
            BigDecimal total = pi.getTotalAmount() != null ? pi.getTotalAmount() : BigDecimal.ZERO;
            PaymentTotals payment = received.getOrDefault(pi.getPiNo(), PaymentTotals.ZERO);
            BigDecimal got = payment.original();
            BigDecimal bal = total.subtract(got);
            BigDecimal totalBase = accountingCurrencyService.toBase(total, pi.getCurrency(), pi.getSignDate());
            BigDecimal balanceBase = totalBase.subtract(payment.base()).max(BigDecimal.ZERO);

            ReceivableRow r = new ReceivableRow();
            r.setPiId(pi.getId());
            r.setPiNo(pi.getPiNo());
            r.setCustomerId(pi.getCustomerId());
            Customer customer = customers.get(pi.getCustomerId());
            if (customer != null) {
                r.setCustomerCode(customer.getCustCode());
                r.setCustomerName(firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(), customer.getCustCode()));
            }
            r.setCurrency(pi.getCurrency());
            r.setSignDate(pi.getSignDate());
            r.setTotalAmount(total);
            r.setDepositAmount(pi.getDepositAmount() == null ? BigDecimal.ZERO : pi.getDepositAmount());
            r.setDepositReceivedAmount(piPaymentService.depositReceivedAmount(pi.getPiNo()));
            r.setReceivedAmount(got);
            r.setBalance(bal);
            r.setBaseCurrency(accountingCurrencyService.baseCurrency());
            r.setTotalAmountBase(totalBase);
            r.setReceivedAmountBase(payment.base());
            r.setBalanceBase(balanceBase);
            r.setSettleStatus(bal.signum() <= 0 ? "PAID" : (got.signum() > 0 ? "PARTIAL" : "UNPAID"));
            Integer age = pi.getSignDate() != null
                    ? (int) ChronoUnit.DAYS.between(pi.getSignDate(), today) : null;
            r.setAgeDays(age);
            r.setOverdue(bal.signum() > 0 && age != null && age > term);
            rows.add(r);
        }
        // 过滤
        String status = query.getStatus();
        boolean overdueOnly = Boolean.TRUE.equals(query.getOverdueOnly());
        return rows.stream()
                .filter(r -> !StringUtils.hasText(status) || status.equals(r.getSettleStatus()))
                .filter(r -> !overdueOnly || Boolean.TRUE.equals(r.getOverdue()))
                .toList();
    }

    /** related_doc_no -> 收款(direction=in)合计; DB 端 GROUP BY SUM, 不把全部流水读进内存 */
    private Map<String, PaymentTotals> receivedByPiNo(List<String> piNos) {
        Map<String, PaymentTotals> map = new HashMap<>();
        if (piNos == null || piNos.isEmpty()) {
            return map;
        }
        List<FinanceEntry> entries = financeMapper.selectList(new LambdaQueryWrapper<FinanceEntry>()
                .eq(FinanceEntry::getDirection, "in")
                .eq(FinanceEntry::getRelatedDocType, "PI")
                .in(FinanceEntry::getRelatedDocNo, piNos));
        for (FinanceEntry entry : entries) {
            if (!StringUtils.hasText(entry.getRelatedDocNo())) {
                continue;
            }
            BigDecimal original = entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO;
            BigDecimal base = accountingCurrencyService.toBase(
                    original, entry.getCurrency(), entry.getEntryDate());
            map.merge(entry.getRelatedDocNo(), new PaymentTotals(original, base), PaymentTotals::add);
        }
        return map;
    }

    private Map<Long, Customer> customers(List<SalesPi> pis) {
        List<Long> ids = pis.stream().map(SalesPi::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Customer> customers = new HashMap<>();
        if (ids.isEmpty()) {
            return customers;
        }
        for (Customer c : customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .select(Customer::getId, Customer::getCustCode, Customer::getCompanyName, Customer::getCustNameCn, Customer::getCustNameEn).in(Customer::getId, ids))) {
            customers.put(c.getId(), c);
        }
        return customers;
    }

    private Long resolveCustomerId(String customerCode, Long fallbackId) {
        if (!StringUtils.hasText(customerCode)) {
            return fallbackId;
        }
        Long id = customerMapper.selectIdByCode(customerCode.trim());
        return id != null ? id : -1L;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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
