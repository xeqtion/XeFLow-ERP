package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ExcelImportValidator;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.dto.FinanceEntryExcel;
import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.dto.FinanceSummary;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceEntryServiceImpl extends ServiceImpl<FinanceEntryMapper, FinanceEntry>
        implements FinanceEntryService {

    private static final SecureRandom ENTRY_NO_RANDOM = new SecureRandom();
    private static final int ENTRY_NO_RANDOM_BOUND = 100000;
    private static final Set<String> SUMMARY_CURRENCIES = Set.of("CNY", "USD");

    private final CustomerMapper customerMapper;
    private final AccountingCurrencyService accountingCurrencyService;

    public FinanceEntryServiceImpl(CustomerMapper customerMapper,
                                   AccountingCurrencyService accountingCurrencyService) {
        this.customerMapper = customerMapper;
        this.accountingCurrencyService = accountingCurrencyService;
    }

    @Override
    public IPage<FinanceEntry> pageQuery(FinanceEntryQuery query) {
        IPage<FinanceEntry> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query));
        hydrateCustomerCodes(page.getRecords());
        return page;
    }

    @Override
    public List<FinanceEntry> listForExport(FinanceEntryQuery query) {
        List<FinanceEntry> entries = this.list(buildWrapper(query));
        hydrateCustomerCodes(entries);
        return entries;
    }

    private LambdaQueryWrapper<FinanceEntry> buildWrapper(FinanceEntryQuery query) {
        Long customerId = resolveCustomerId(query.getCustomerCode(), query.getCustomerId());
        return new LambdaQueryWrapper<FinanceEntry>()
                .eq(StringUtils.hasText(query.getType()), FinanceEntry::getType, query.getType())
                .eq(customerId != null, FinanceEntry::getCustomerId, customerId)
                .ge(query.getStartDate() != null, FinanceEntry::getEntryDate, query.getStartDate())
                .le(query.getEndDate() != null, FinanceEntry::getEntryDate, query.getEndDate())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(FinanceEntry::getEntryNo, query.getKeyword())
                        .or().like(FinanceEntry::getRelatedDocNo, query.getKeyword()))
                .orderByDesc(FinanceEntry::getEntryDate, FinanceEntry::getId);
    }

    @Override
    public void fillDirection(FinanceEntry entry) {
        FinanceType type = FinanceType.of(entry.getType());
        if (type == null) {
            return;
        }
        if (!StringUtils.hasText(entry.getDirection())) {
            entry.setDirection(type.getDirection());
        }
    }

    @Override
    public List<FinanceSummary> summary(FinanceEntryQuery query) {
        List<FinanceEntry> entries = this.list(buildWrapper(query));
        Map<String, FinanceSummary> grouped = new TreeMap<>();
        for (FinanceEntry entry : entries) {
            FinanceType type = FinanceType.of(entry.getType());
            if (type == null) {
                continue;
            }
            String currency = normalizedCurrency(entry.getCurrency());
            if (!SUMMARY_CURRENCIES.contains(currency)) {
                continue;
            }
            String direction = type.getDirection();
            String key = direction + "|" + currency;
            FinanceSummary summary = grouped.computeIfAbsent(
                    key, ignored -> newDirectionSummary(direction, currency));
            summary.setTotalAmount(summary.getTotalAmount().add(zero(entry.getAmount())));
            summary.setCount(summary.getCount() + 1);
        }
        return new ArrayList<>(grouped.values());
    }

    @Override
    public List<FinanceSummary> summaryInBase(FinanceEntryQuery query) {
        List<FinanceEntry> entries = this.list(buildWrapper(query));
        List<FinanceSummary> result = new ArrayList<>();
        for (FinanceType type : FinanceType.values()) {
            FinanceSummary s = newSummary(type, accountingCurrencyService.baseCurrency());
            BigDecimal total = BigDecimal.ZERO;
            long count = 0;
            for (FinanceEntry e : entries) {
                if (type.name().equals(e.getType())) {
                    total = total.add(accountingCurrencyService.toBase(
                            e.getAmount(), e.getCurrency(), e.getEntryDate()));
                    count++;
                }
            }
            s.setTotalAmount(total);
            s.setCount(count);
            result.add(s);
        }
        return result;
    }

    private FinanceSummary newSummary(FinanceType type, String currency) {
        FinanceSummary summary = new FinanceSummary();
        summary.setType(type.name());
        summary.setLabel(type.getLabel());
        summary.setDirection(type.getDirection());
        summary.setCurrency(currency);
        summary.setTotalAmount(BigDecimal.ZERO);
        return summary;
    }

    private FinanceSummary newDirectionSummary(String direction, String currency) {
        FinanceSummary summary = new FinanceSummary();
        summary.setType(direction.toUpperCase());
        summary.setLabel("in".equals(direction) ? "收入" : "支出");
        summary.setDirection(direction);
        summary.setCurrency(currency);
        summary.setTotalAmount(BigDecimal.ZERO);
        return summary;
    }

    private String normalizedCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "CNY";
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public boolean save(FinanceEntry entity) {
        prepareBeforeSave(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(FinanceEntry entity) {
        fillCustomerId(entity);
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file) throws IOException {
        ExcelImportValidator.validateFile(file);
        ImportResult result = new ImportResult();
        List<FinanceEntryExcel> rows = new ArrayList<>();
        ExcelUtil.read(file.getInputStream(), FinanceEntryExcel.class, 500, rows::addAll);
        ExcelImportValidator.validateRowCount(rows.size());
        result.setTotal(rows.size());

        List<FinanceEntry> toSave = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            FinanceEntryExcel row = rows.get(i);
            int rowNo = i + 1;
            try {
                if (FinanceType.of(row.getType()) == null) {
                    throw new BusinessException("类型非法: " + row.getType());
                }
                if (row.getAmount() == null) {
                    throw new BusinessException("金额不能为空");
                }
                FinanceEntry entry = new FinanceEntry();
                BeanUtils.copyProperties(row, entry);
                entry.setCustomerCode(row.getCustomerCode());
                if (!StringUtils.hasText(entry.getCurrency())) {
                    entry.setCurrency("USD");
                } else {
                    entry.setCurrency(entry.getCurrency().trim().toUpperCase());
                }
                validateImportFields(entry);
                prepareBeforeSave(entry);
                toSave.add(entry);
                result.addSuccess(rowNo);
            } catch (BusinessException e) {
                result.addError(rowNo, e.getMessage());
            }
        }
        if (!toSave.isEmpty()) {
            this.saveBatch(toSave);
        }
        return result;
    }

    private void prepareBeforeSave(FinanceEntry entry) {
        fillDirection(entry);
        fillCustomerId(entry);
        fillEntryNo(entry);
    }

    private void validateImportFields(FinanceEntry entry) {
        ExcelImportValidator.validateText("流水号", entry.getEntryNo(), 64);
        ExcelImportValidator.validateText("类型", entry.getType(), 32);
        ExcelImportValidator.validateText("方向", entry.getDirection(), 8);
        ExcelImportValidator.validateText("币种", entry.getCurrency(), 8);
        ExcelImportValidator.validateText("关联单据类型", entry.getRelatedDocType(), 32);
        ExcelImportValidator.validateText("关联单据号", entry.getRelatedDocNo(), 64);
        ExcelImportValidator.validateText("备注", entry.getRemark(), 512);
        ExcelImportValidator.validateDecimal("金额", entry.getAmount(), 14, 4);
        if (StringUtils.hasText(entry.getDirection())
                && !Set.of("in", "out").contains(entry.getDirection().trim().toLowerCase())) {
            throw new BusinessException("方向仅支持 in 或 out，也可以留空由系统自动填写");
        }
        if (StringUtils.hasText(entry.getDirection())) {
            entry.setDirection(entry.getDirection().trim().toLowerCase());
        }
    }

    private void fillEntryNo(FinanceEntry entry) {
        if (entry == null || StringUtils.hasText(entry.getEntryNo())) {
            return;
        }
        String direction = StringUtils.hasText(entry.getDirection()) ? entry.getDirection() : directionOf(entry.getType());
        String prefix = "out".equals(direction) ? "FK" : "SK";
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String entryNo;
        do {
            entryNo = prefix + datePart + "-" + String.format("%05d", ENTRY_NO_RANDOM.nextInt(ENTRY_NO_RANDOM_BOUND));
        } while (this.count(new LambdaQueryWrapper<FinanceEntry>().eq(FinanceEntry::getEntryNo, entryNo)) > 0);
        entry.setEntryNo(entryNo);
    }

    private String directionOf(String typeCode) {
        FinanceType type = FinanceType.of(typeCode);
        return type != null ? type.getDirection() : null;
    }

    private Long resolveCustomerId(String customerCode, Long fallbackId) {
        if (!StringUtils.hasText(customerCode)) {
            return fallbackId;
        }
        Long id = customerMapper.selectIdByCode(customerCode.trim());
        return id != null ? id : -1L;
    }

    private void fillCustomerId(FinanceEntry entry) {
        if (entry == null || entry.getCustomerId() != null || !StringUtils.hasText(entry.getCustomerCode())) {
            return;
        }
        Long id = customerMapper.selectIdByCode(entry.getCustomerCode().trim());
        if (id == null) {
            throw new BusinessException("客户ID不存在: " + entry.getCustomerCode());
        }
        entry.setCustomerId(id);
    }

    private void hydrateCustomerCodes(List<FinanceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<Long> ids = entries.stream().map(FinanceEntry::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Customer> customers = customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a));
        for (FinanceEntry entry : entries) {
            Customer customer = customers.get(entry.getCustomerId());
            if (customer != null) {
                entry.setCustomerCode(customer.getCustCode());
            }
        }
    }
}
