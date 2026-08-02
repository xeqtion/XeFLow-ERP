package com.erp.order.service;

import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.security.DataAccessGuard;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.entity.ShippingInstructionItem;
import com.erp.order.mapper.ShippingInstructionItemMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.task.service.PersonalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingInstructionServiceImplTest {

    private CustomerMapper customerMapper;
    private SalesPiMapper salesPiMapper;
    private SalesPiItemMapper salesPiItemMapper;
    private DataAccessGuard accessGuard;
    private ShippingInstructionServiceImpl service;

    @BeforeEach
    void setUp() {
        customerMapper = mock(CustomerMapper.class);
        salesPiMapper = mock(SalesPiMapper.class);
        salesPiItemMapper = mock(SalesPiItemMapper.class);
        accessGuard = mock(DataAccessGuard.class);
        service = new ShippingInstructionServiceImpl(
                customerMapper,
                salesPiMapper,
                salesPiItemMapper,
                mock(ShippingInstructionItemMapper.class),
                mock(SysUserMapper.class),
                accessGuard,
                mock(PersonalTaskService.class));

        SalesPi pi = new SalesPi();
        pi.setId(10L);
        pi.setPiNo("PI-001");
        pi.setCustomerId(30L);
        pi.setStatus("ORDERED");
        when(salesPiMapper.selectById(10L)).thenReturn(pi);
        when(salesPiItemMapper.selectList(any())).thenReturn(List.of());

        Customer piCustomer = new Customer();
        piCustomer.setId(30L);
        piCustomer.setCustCode("PI-CUSTOMER");
        piCustomer.setContactName("PI Contact");
        piCustomer.setPhone("PI Phone");
        piCustomer.setAddress("PI Address");
        when(customerMapper.selectById(30L)).thenReturn(piCustomer);
    }

    @Test
    void linkedPiAllowsIndependentCustomerAndContactSnapshot() {
        when(customerMapper.selectIdByCode("CUSTOM-20")).thenReturn(20L);
        ShippingInstruction instruction = baseInstruction();
        instruction.setId(100L);
        instruction.setCustomerId(20L);
        instruction.setCustomerCode("CUSTOM-20");
        instruction.setContactName("Edited Contact");
        instruction.setPhone("Edited Phone");
        instruction.setDeliveryAddress("Edited Address");

        ReflectionTestUtils.invokeMethod(service, "prepare", instruction);

        assertThat(instruction.getCustomerId()).isEqualTo(20L);
        assertThat(instruction.getCustomerCode()).isEqualTo("CUSTOM-20");
        assertThat(instruction.getContactName()).isEqualTo("Edited Contact");
        assertThat(instruction.getPhone()).isEqualTo("Edited Phone");
        assertThat(instruction.getDeliveryAddress()).isEqualTo("Edited Address");
        verify(accessGuard).assertPiVisible(10L);
        verify(accessGuard, never()).assertPiAndCustomerVisible(any(), any());
    }

    @Test
    void linkedPiStillSuppliesDefaultsForNewInstruction() {
        ShippingInstruction instruction = baseInstruction();

        ReflectionTestUtils.invokeMethod(service, "prepare", instruction);

        assertThat(instruction.getCustomerId()).isEqualTo(30L);
        assertThat(instruction.getCustomerCode()).isEqualTo("PI-CUSTOMER");
        assertThat(instruction.getContactName()).isEqualTo("PI Contact");
        assertThat(instruction.getPhone()).isEqualTo("PI Phone");
        assertThat(instruction.getDeliveryAddress()).isEqualTo("PI Address");
        assertThat(instruction.getOrderNo()).isEqualTo("PI-001");
    }

    @Test
    void editingDoesNotRefillBlankShippingSnapshotFromPi() {
        ShippingInstruction instruction = baseInstruction();
        instruction.setId(100L);

        ReflectionTestUtils.invokeMethod(service, "prepare", instruction);

        assertThat(instruction.getCustomerId()).isNull();
        assertThat(instruction.getCustomerCode()).isNull();
        assertThat(instruction.getContactName()).isNull();
        assertThat(instruction.getPhone()).isNull();
        assertThat(instruction.getDeliveryAddress()).isNull();
        assertThat(instruction.getOrderNo()).isNull();
    }

    @Test
    void submittedProductRowsAreKeptSeparateAndSummarizedForLegacyFields() {
        ShippingInstruction instruction = baseInstruction();
        ShippingInstructionItem first = instructionItem("A12-1-100001", "A12-1", "10", null);
        ShippingInstructionItem second = instructionItem("A12-2-100002", "A12-2", "5", "3");
        instruction.setItems(List.of(first, second));

        List<ShippingInstructionItem> submitted = ReflectionTestUtils.invokeMethod(
                service, "normalizeItems", instruction.getItems());
        List<ShippingInstructionItem> legacy = ReflectionTestUtils.invokeMethod(
                service, "legacyItems", instruction);
        ReflectionTestUtils.invokeMethod(service, "prepare", instruction);
        List<ShippingInstructionItem> resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveItemsForCreate", instruction, submitted, legacy);
        ReflectionTestUtils.invokeMethod(service, "syncLegacySummary", instruction, resolved);

        assertThat(resolved).hasSize(2);
        assertThat(resolved.get(0).getProductModel()).isEqualTo("A12-1-100001");
        assertThat(resolved.get(0).getCartonCount()).isEqualByComparingTo("10");
        assertThat(instruction.getProductModel()).isEqualTo("A12-1-100001\nA12-2-100002");
        assertThat(instruction.getQuantity()).isEqualByComparingTo("15");
        assertThat(instruction.getCartonCount()).isEqualByComparingTo("13");
    }

    @Test
    void legacyClientProductFieldsAreNotOverwrittenByLinkedPiItems() {
        SalesPiItem piItem = new SalesPiItem();
        piItem.setProductModel("PI-MODEL");
        piItem.setFactoryModel("PI-FACTORY");
        piItem.setQty(new BigDecimal("99"));
        when(salesPiItemMapper.selectList(any())).thenReturn(List.of(piItem));

        ShippingInstruction instruction = baseInstruction();
        instruction.setProductModel("MANUAL-MODEL");
        instruction.setFactoryModel("MANUAL-FACTORY");
        instruction.setQuantity(new BigDecimal("7"));
        List<ShippingInstructionItem> submitted = ReflectionTestUtils.invokeMethod(
                service, "normalizeItems", instruction.getItems());
        List<ShippingInstructionItem> legacy = ReflectionTestUtils.invokeMethod(
                service, "legacyItems", instruction);

        ReflectionTestUtils.invokeMethod(service, "prepare", instruction);
        List<ShippingInstructionItem> resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveItemsForCreate", instruction, submitted, legacy);

        assertThat(resolved).singleElement().satisfies(item -> {
            assertThat(item.getProductModel()).isEqualTo("MANUAL-MODEL");
            assertThat(item.getFactoryModel()).isEqualTo("MANUAL-FACTORY");
            assertThat(item.getQuantity()).isEqualByComparingTo("7");
        });
    }

    @Test
    void historicalSnapshotOnlyExpandsFromPiWhileItsSummaryStillMatches() {
        ShippingInstruction instruction = baseInstruction();
        instruction.setProductModel("MODEL-A\nMODEL-B");
        instruction.setFactoryModel("FACTORY-A\nFACTORY-B");
        instruction.setQuantity(new BigDecimal("15"));
        List<ShippingInstructionItem> piItems = List.of(
                instructionItem("MODEL-A", "FACTORY-A", "10", "10"),
                instructionItem("MODEL-B", "FACTORY-B", "5", "5"));

        Boolean matches = ReflectionTestUtils.invokeMethod(
                service, "legacySummaryMatchesPi", instruction, piItems);
        assertThat(matches).isTrue();

        instruction.setQuantity(new BigDecimal("12"));
        Boolean changed = ReflectionTestUtils.invokeMethod(
                service, "legacySummaryMatchesPi", instruction, piItems);
        assertThat(changed).isFalse();
    }

    private ShippingInstructionItem instructionItem(String productModel,
                                                     String factoryModel,
                                                     String quantity,
                                                     String cartonCount) {
        ShippingInstructionItem item = new ShippingInstructionItem();
        item.setProductModel(productModel);
        item.setFactoryModel(factoryModel);
        item.setQuantity(new BigDecimal(quantity));
        item.setCartonCount(cartonCount == null ? null : new BigDecimal(cartonCount));
        return item;
    }

    private ShippingInstruction baseInstruction() {
        ShippingInstruction instruction = new ShippingInstruction();
        instruction.setSiNo("SI-TEST-001");
        instruction.setPiId(10L);
        return instruction;
    }
}
