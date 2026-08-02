package com.erp.report.service;

import com.erp.common.security.DataScopeHelper;
import com.erp.finance.service.AccountingCurrencyService;
import com.erp.report.dto.ReportAmountSource;
import com.erp.report.dto.ReportQuery;
import com.erp.report.dto.ReportRow;
import com.erp.report.dto.SalesmanRow;
import com.erp.report.dto.SalesmanTrendRow;
import com.erp.report.mapper.ReportMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private final ReportMapper mapper = mock(ReportMapper.class);
    private final DataScopeHelper dataScope = mock(DataScopeHelper.class);
    private final AccountingCurrencyService currency = mock(AccountingCurrencyService.class);
    private final ReportService service = new ReportService(mapper, dataScope, currency);

    @Test
    void aggregatesMixedCurrenciesIntoCnyByBusinessDate() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        ReportAmountSource usd = source(date, "USD", "10");
        ReportAmountSource eur = source(date, "EUR", "20");
        when(mapper.purchaseSources(null, null)).thenReturn(List.of(usd, eur));
        when(currency.baseCurrency()).thenReturn("CNY");
        when(currency.toBase(new BigDecimal("10"), "USD", date)).thenReturn(new BigDecimal("72.50"));
        when(currency.toBase(new BigDecimal("20"), "EUR", date)).thenReturn(new BigDecimal("156.00"));

        ReportQuery query = new ReportQuery();
        query.setDimension("DAY");
        List<ReportRow> rows = service.purchaseReport(query);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPeriod()).isEqualTo("2026-07-20");
            assertThat(row.getAmount()).isEqualByComparingTo("228.50");
            assertThat(row.getCurrency()).isEqualTo("CNY");
        });
    }

    @Test
    void calculatesRevenueShareAndQuarterlySalesmanTrend() {
        ReportAmountSource alice = salesmanSource(LocalDate.of(2026, 2, 10), 1L, "Alice", "100");
        ReportAmountSource bob = salesmanSource(LocalDate.of(2026, 5, 12), 2L, "Bob", "300");
        when(mapper.salesmanSources(null, null)).thenReturn(List.of(alice, bob));
        when(currency.baseCurrency()).thenReturn("CNY");
        when(currency.toBase(new BigDecimal("100"), "USD", alice.getBusinessDate()))
                .thenReturn(new BigDecimal("700"));
        when(currency.toBase(new BigDecimal("300"), "USD", bob.getBusinessDate()))
                .thenReturn(new BigDecimal("2100"));

        List<SalesmanRow> ranking = service.salesBySalesman(new ReportQuery());
        assertThat(ranking).extracting(SalesmanRow::getSalesman).containsExactly("Bob", "Alice");
        assertThat(ranking).extracting(SalesmanRow::getRevenueShare)
                .containsExactly(new BigDecimal("75.00"), new BigDecimal("25.00"));

        ReportQuery quarterly = new ReportQuery();
        quarterly.setDimension("QUARTER");
        List<SalesmanTrendRow> trend = service.salesTrendBySalesman(quarterly);
        assertThat(trend).extracting(SalesmanTrendRow::getPeriod)
                .containsExactly("2026-Q1", "2026-Q2");
    }

    private ReportAmountSource source(LocalDate date, String code, String amount) {
        ReportAmountSource source = new ReportAmountSource();
        source.setBusinessDate(date);
        source.setCurrency(code);
        source.setAmount(new BigDecimal(amount));
        return source;
    }

    private ReportAmountSource salesmanSource(LocalDate date, Long ownerId, String salesman, String amount) {
        ReportAmountSource source = source(date, "USD", amount);
        source.setOwnerId(ownerId);
        source.setSalesman(salesman);
        return source;
    }
}
