package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.dto.FinanceSummary;
import com.erp.finance.entity.FinanceEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class FinanceEntrySummaryTest {

    @Test
    void groupsDifferentEntryTypesByDirectionAndCurrency() {
        FinanceEntryServiceImpl service = spy(new FinanceEntryServiceImpl(
                mock(CustomerMapper.class), mock(AccountingCurrencyService.class)));
        doReturn(List.of(
                entry("RECEIVABLE", "in", "CNY", "38236"),
                entry("PRE_RECEIVE", "in", "CNY", "14244"),
                entry("RECEIVABLE", "in", "USD", "1800"),
                entry("PAYABLE", "out", "CNY", "6080")
        )).when(service).list(any(Wrapper.class));

        List<FinanceSummary> summaries = service.summary(new FinanceEntryQuery());

        assertThat(summaries).hasSize(3);
        assertThat(summaries)
                .filteredOn(item -> "in".equals(item.getDirection()) && "CNY".equals(item.getCurrency()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getTotalAmount()).isEqualByComparingTo("52480");
                    assertThat(item.getCount()).isEqualTo(2);
                });
        assertThat(summaries)
                .filteredOn(item -> "in".equals(item.getDirection()) && "USD".equals(item.getCurrency()))
                .singleElement()
                .extracting(FinanceSummary::getTotalAmount)
                .isEqualTo(new BigDecimal("1800"));
        assertThat(summaries)
                .filteredOn(item -> "out".equals(item.getDirection()) && "CNY".equals(item.getCurrency()))
                .singleElement()
                .extracting(FinanceSummary::getTotalAmount)
                .isEqualTo(new BigDecimal("6080"));
    }

    private FinanceEntry entry(String type, String direction, String currency, String amount) {
        FinanceEntry entry = new FinanceEntry();
        entry.setType(type);
        entry.setDirection(direction);
        entry.setCurrency(currency);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }
}
