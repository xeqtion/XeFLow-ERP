package com.erp.shipment.service;

import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataAccessGuard;
import com.erp.finance.service.PiPaymentService;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.shipment.entity.Shipment;
import com.erp.shipment.mapper.ShipmentItemMapper;
import com.erp.shipment.mapper.ShipmentMapper;
import com.erp.task.service.PersonalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipmentReferencePrecisionTest {
    private static final long ID = 9007199254740993L;
    private ShipmentMapper mapper;
    private SalesPiMapper piMapper;
    private DataAccessGuard guard;
    private PiPaymentService paymentService;
    private ShipmentServiceImpl service;
    private Shipment existing;

    @BeforeEach
    void setUp() {
        mapper = mock(ShipmentMapper.class);
        piMapper = mock(SalesPiMapper.class);
        guard = mock(DataAccessGuard.class);
        paymentService = mock(PiPaymentService.class);
        service = new ShipmentServiceImpl(guard, mock(PersonalTaskService.class), piMapper,
                mock(ShippingInstructionMapper.class), paymentService, mock(ShipmentItemMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        existing = new Shipment();
        existing.setId(ID);
        existing.setPiId(ID + 2);
        existing.setSiId(ID + 4);
        existing.setCreateBy(1L);
        existing.setShipmentNo("SHP-REGRESSION");
        existing.setStatus("FULL_SHIPPED");
        when(mapper.selectById(ID)).thenReturn(existing);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesUsingPersistedReferencesForRoundedLegacyOrMinimalNewPayload(boolean legacy) {
        SalesPi pi = new SalesPi();
        pi.setId(ID + 2);
        when(piMapper.selectById(ID + 2)).thenReturn(pi);
        when(mapper.updateById(any(Shipment.class))).thenReturn(1);
        Shipment update = update();
        if (legacy) {
            update.setPiId((long) (double) (ID + 2));
            update.setSiId((long) (double) (ID + 4));
            update.setStatus(existing.getStatus());
        }

        assertThat(service.updateById(update)).isTrue();

        assertThat(update.getPiId()).isEqualTo(ID + 2);
        assertThat(update.getSiId()).isEqualTo(ID + 4);
        assertThat(update.getShipmentNo()).isEqualTo("SHP-REGRESSION");
        assertThat(update.getStatus()).isEqualTo("FULL_SHIPPED");
        assertThat(update.getVolumeCbm()).isEqualByComparingTo("13.5");
        verify(guard).assertPiVisible(ID + 2);
        verify(paymentService).assertFullyPaid(pi);
        verify(mapper).updateById(update);
    }

    @Test
    void stillChecksVisibilityOfOriginalShipmentBeforeConsideringSubmittedReferences() {
        doThrow(new BusinessException("无权访问出货单"))
                .when(guard).assertRecordVisible(ID + 2, null, 1L, "出货单");
        Shipment update = update();
        update.setPiId(1L);
        assertThatThrownBy(() -> service.updateById(update)).hasMessage("无权访问出货单");
        verify(mapper, never()).updateById(any(Shipment.class));
    }

    @Test
    void doesNotAllowManualStatusChangeOrMissingShipmentUpdate() {
        Shipment update = update();
        update.setStatus("DRAFT");
        assertThatThrownBy(() -> service.updateById(update)).hasMessageContaining("不能手工修改");
        when(mapper.selectById(ID)).thenReturn(null);
        assertThatThrownBy(() -> service.updateById(update)).hasMessage("出货单不存在");
        verify(mapper, never()).updateById(any(Shipment.class));
    }

    private Shipment update() {
        Shipment update = new Shipment();
        update.setId(ID);
        update.setVolumeCbm(new BigDecimal("13.5"));
        return update;
    }
}
