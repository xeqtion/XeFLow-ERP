package com.erp.finance.service;

import com.erp.basedata.service.CurrencyService;
import com.erp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingCurrencyServiceTest {

    private final CurrencyService currencyService = mock(CurrencyService.class);
    private final AccountingCurrencyService service = new AccountingCurrencyService(currencyService);

    @Test
    void convertsUsingBusinessDate() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(currencyService.convert(new BigDecimal("10"), "USD", date))
                .thenReturn(new BigDecimal("72.50"));

        assertThat(service.toBase(new BigDecimal("10"), "USD", date))
                .isEqualByComparingTo("72.50");
        verify(currencyService).convert(new BigDecimal("10"), "USD", date);
    }

    @Test
    void fallsBackToLatestRateWhenHistoricalRateDoesNotExist() {
        LocalDate date = LocalDate.of(2020, 1, 1);
        when(currencyService.convert(new BigDecimal("10"), "USD", date))
                .thenThrow(new BusinessException("missing"));
        when(currencyService.convert(new BigDecimal("10"), "USD", null))
                .thenReturn(new BigDecimal("71.00"));

        assertThat(service.toBase(new BigDecimal("10"), "USD", date))
                .isEqualByComparingTo("71.00");
    }

    @Test
    void refusesToMixUnconvertibleForeignCurrencyIntoBaseTotals() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(currencyService.baseCurrency()).thenReturn("CNY");
        when(currencyService.convert(new BigDecimal("10"), "XYZ", date))
                .thenThrow(new BusinessException("missing"));
        when(currencyService.convert(new BigDecimal("10"), "XYZ", null))
                .thenThrow(new BusinessException("missing"));

        assertThatThrownBy(() -> service.toBase(new BigDecimal("10"), "XYZ", date))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("XYZ")
                .hasMessageContaining("CNY");
    }
}
