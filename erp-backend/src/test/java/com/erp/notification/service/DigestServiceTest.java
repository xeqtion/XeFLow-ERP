package com.erp.notification.service;

import com.erp.finance.service.FinanceEntryService;
import com.erp.notification.config.DigestProperties;
import com.erp.notification.constant.NotificationType;
import com.erp.report.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigestServiceTest {

    private final ReportService reportService = mock(ReportService.class);
    private final FinanceEntryService financeService = mock(FinanceEntryService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DigestProperties properties = new DigestProperties();
    private final DigestService service = new DigestService(
            reportService, financeService, notificationService, properties);

    @Test
    void weeklyTitleIsConcise() {
        assertThat(service.title(DigestService.Period.WEEKLY,
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)))
                .isEqualTo("经营周报");
    }

    @Test
    void weeklyDigestOnlyTargetsFinanceAndAdministrators() {
        LocalDate start = LocalDate.of(2026, 7, 13);
        LocalDate end = LocalDate.of(2026, 7, 19);
        properties.setSendEmail(false);
        when(reportService.profitReport(any())).thenReturn(List.of());
        when(financeService.summaryInBase(any())).thenReturn(List.of());
        when(notificationService.broadcastToRoles(any(), anyString(), anyString(),
                eq(NotificationType.REPORT), anyBoolean())).thenReturn(3);

        assertThat(service.send(DigestService.Period.WEEKLY, start, end)).isEqualTo(3);

        verify(notificationService).broadcastToRoles(
                eq(Set.of("finance", "admin", "super_admin")),
                eq("经营周报"), anyString(), eq(NotificationType.REPORT), eq(false));
        verify(notificationService, never()).broadcast(
                anyString(), anyString(), eq(NotificationType.REPORT), anyBoolean());
    }
}
