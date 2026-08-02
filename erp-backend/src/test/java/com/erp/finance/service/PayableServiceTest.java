package com.erp.finance.service;

import com.erp.common.security.DataAccessGuard;
import com.erp.finance.dto.PayableQuery;
import com.erp.finance.dto.PayableSummary;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.mapper.PurchaseOrderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayableServiceTest {

    private final PurchaseOrderMapper poMapper = mock(PurchaseOrderMapper.class);
    private final FinanceEntryMapper financeMapper = mock(FinanceEntryMapper.class);
    private final FinanceEntryService entryService = mock(FinanceEntryService.class);
    private final AccountingCurrencyService currencyService = mock(AccountingCurrencyService.class);
    private final DataAccessGuard accessGuard = mock(DataAccessGuard.class);
    private final PayableService service = new PayableService(
            poMapper, financeMapper, entryService, currencyService, accessGuard);

    @Test
    void settledUsdOrderHasNoBalanceDespiteDifferentCnyRates() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setPoNo("PO-USD-001");
        po.setCurrency("USD");
        po.setStatus("COMPLETED");
        po.setOrderDate(LocalDate.of(2026, 7, 7));
        po.setTotalAmount(new BigDecimal("2800"));

        FinanceEntry payment = new FinanceEntry();
        payment.setDirection("out");
        payment.setRelatedDocNo(po.getPoNo());
        payment.setCurrency("USD");
        payment.setEntryDate(LocalDate.of(2026, 7, 21));
        payment.setAmount(new BigDecimal("2800"));

        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(financeMapper.selectList(any())).thenReturn(List.of(payment));
        when(currencyService.baseCurrency()).thenReturn("CNY");
        when(currencyService.toBase(po.getTotalAmount(), "USD", po.getOrderDate()))
                .thenReturn(new BigDecimal("19480.67"));
        when(currencyService.toBase(payment.getAmount(), "USD", payment.getEntryDate()))
                .thenReturn(new BigDecimal("19458.97"));

        List<PayableSummary> summaries = service.summary(new PayableQuery());

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getCurrency()).isEqualTo("USD");
            assertThat(summary.getTotalPayable()).isEqualByComparingTo("2800");
            assertThat(summary.getTotalPaid()).isEqualByComparingTo("2800");
            assertThat(summary.getTotalBalance()).isEqualByComparingTo("0");
            assertThat(summary.getOpenCount()).isZero();
        });
    }

    @Test
    void financeSummaryOnlyIncludesCnyAndUsd() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(2L);
        po.setPoNo("PO-EUR-001");
        po.setCurrency("EUR");
        po.setStatus("CONFIRMED");
        po.setOrderDate(LocalDate.of(2026, 7, 21));
        po.setTotalAmount(new BigDecimal("100"));

        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(financeMapper.selectList(any())).thenReturn(List.of());
        when(currencyService.baseCurrency()).thenReturn("CNY");
        when(currencyService.toBase(po.getTotalAmount(), "EUR", po.getOrderDate()))
                .thenReturn(new BigDecimal("840"));

        assertThat(service.summary(new PayableQuery())).isEmpty();
    }
}
