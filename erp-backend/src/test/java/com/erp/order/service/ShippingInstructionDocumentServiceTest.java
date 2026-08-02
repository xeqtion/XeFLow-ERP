package com.erp.order.service;

import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.entity.ShippingInstructionItem;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingInstructionDocumentServiceTest {

    private SalesPiMapper salesPiMapper;
    private SalesPiItemMapper salesPiItemMapper;
    private CustomerMapper customerMapper;
    private ShippingInstructionDocumentService service;

    @BeforeEach
    void setUp() {
        salesPiMapper = mock(SalesPiMapper.class);
        salesPiItemMapper = mock(SalesPiItemMapper.class);
        customerMapper = mock(CustomerMapper.class);
        service = new ShippingInstructionDocumentService(salesPiMapper, salesPiItemMapper, customerMapper);
    }

    @Test
    void linkedPiItemsAreInsertedFromTemplateRowTen() throws Exception {
        SalesPi pi = new SalesPi();
        pi.setId(10L);
        pi.setShippingMarks("XeFlow / MOSCOW");
        when(salesPiMapper.selectById(10L)).thenReturn(pi);

        SalesPiItem first = item(101L, "M280-100058", "M280", "20");
        SalesPiItem second = item(102L, "AD-402", "AD402", "10");
        when(salesPiItemMapper.selectList(any())).thenReturn(List.of(first, second));

        Customer customer = new Customer();
        customer.setId(20L);
        customer.setCompanyName("ANZHEE");
        customer.setCountry("俄罗斯");
        when(customerMapper.selectById(20L)).thenReturn(customer);

        ShippingInstruction instruction = instruction();
        try (Workbook workbook = service.createWorkbook(instruction)) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            assertThat(sheet.getSheetName()).isEqualTo("出货指示");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("出货编号：SI-20260722-001");
            assertThat(sheet.getRow(9).getCell(1).getStringCellValue()).isEqualTo("ANZHEE（俄罗斯）");
            assertThat(sheet.getRow(9).getCell(2).getStringCellValue()).isEqualTo("M280-100058");
            assertThat(sheet.getRow(10).getCell(2).getStringCellValue()).isEqualTo("AD-402");
            assertThat(sheet.getRow(10).getCell(3).getStringCellValue()).isEqualTo("AD402");
            assertThat(sheet.getRow(9).getCell(4).getNumericCellValue()).isEqualTo(20d);
            assertThat(sheet.getRow(10).getCell(4).getNumericCellValue()).isEqualTo(10d);
            assertThat(sheet.getRow(9).getCell(5).getNumericCellValue()).isEqualTo(10d);
            assertThat(sheet.getRow(10).getCell(5).getNumericCellValue()).isEqualTo(5d);
            assertThat(sheet.getRow(9).getCell(6).getStringCellValue()).isEqualTo("加贴客户标签");
            assertThat(sheet.getRow(9).getCell(7).getStringCellValue()).isEqualTo("XeFlow / MOSCOW");
            assertThat(sheet.getRow(3).getCell(8).getStringCellValue()).isEqualTo("优先安排发货");

            assertThat(sheet.getRow(11).getCell(1).getStringCellValue()).isEqualTo("小计");
            assertThat(sheet.getRow(11).getCell(4).getCellFormula()).isEqualTo("SUM(E10:E11)");
            assertThat(sheet.getRow(11).getCell(5).getCellFormula()).isEqualTo("SUM(F10:F11)");
            assertThat(evaluator.evaluate(sheet.getRow(11).getCell(4)).getNumberValue()).isEqualTo(30d);
            assertThat(evaluator.evaluate(sheet.getRow(11).getCell(5)).getNumberValue()).isEqualTo(15d);

            assertThat(sheet.getRow(12).getCell(1).getStringCellValue()).isEqualTo("业务：Alice");
            assertThat(sheet.getRow(12).getCell(3).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(12).getCell(6).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(10).getHeight()).isEqualTo(sheet.getRow(9).getHeight());
            assertThat(sheet.getRow(10).getCell(2).getCellStyle())
                    .isEqualTo(sheet.getRow(9).getCell(2).getCellStyle());

            assertThat(sheet.getMergedRegions()).extracting(region -> region.formatAsString()).contains(
                    "B10:B11",
                    "G10:G12",
                    "H10:H12",
                    "I4:I12",
                    "B12:D12",
                    "D13:E13");
            assertThat(workbook.getPrintArea(0)).contains("$B$3:$I$13");
            writePreviewIfRequested(workbook);
        }
    }

    @Test
    void unlinkedInstructionKeepsSingleTemplateDetailRow() throws Exception {
        when(salesPiItemMapper.selectList(any())).thenReturn(List.of());
        ShippingInstruction instruction = instruction();
        instruction.setPiId(null);
        instruction.setProductModel("XF-TL-A");
        instruction.setFactoryModel("FAC-TL-A");
        instruction.setQuantity(new BigDecimal("8"));
        instruction.setCartonCount(null);

        try (Workbook workbook = service.createWorkbook(instruction)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(9).getCell(2).getStringCellValue()).isEqualTo("XF-TL-A");
            assertThat(sheet.getRow(9).getCell(4).getNumericCellValue()).isEqualTo(8d);
            assertThat(sheet.getRow(9).getCell(5).getNumericCellValue()).isEqualTo(8d);
            assertThat(sheet.getRow(10).getCell(1).getStringCellValue()).isEqualTo("小计");
            assertThat(sheet.getRow(11).getCell(1).getStringCellValue()).isEqualTo("业务：Alice");
            assertThat(workbook.getPrintArea(0)).contains("$B$3:$I$12");
        }
    }

    @Test
    void savedInstructionItemsTakePrecedenceOverLinkedPiAndMergeCustomerCell() throws Exception {
        SalesPi pi = new SalesPi();
        pi.setId(10L);
        when(salesPiMapper.selectById(10L)).thenReturn(pi);
        when(salesPiItemMapper.selectList(any())).thenReturn(List.of(
                item(101L, "PI-MODEL", "PI-FACTORY", "99")));

        Customer customer = new Customer();
        customer.setId(20L);
        customer.setCompanyName("EX PRODUCTION");
        customer.setCountry("亚美尼亚");
        when(customerMapper.selectById(20L)).thenReturn(customer);

        ShippingInstruction instruction = instruction();
        instruction.setOrderCountry("亚美尼亚");
        instruction.setItems(List.of(
                instructionItem("A12-1-100001", "A12-1", "10"),
                instructionItem("A12-2-100002", "A12-2", "10")));

        try (Workbook workbook = service.createWorkbook(instruction)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(9).getCell(1).getStringCellValue())
                    .isEqualTo("EX PRODUCTION（亚美尼亚）");
            assertThat(sheet.getRow(9).getCell(2).getStringCellValue()).isEqualTo("A12-1-100001");
            assertThat(sheet.getRow(10).getCell(2).getStringCellValue()).isEqualTo("A12-2-100002");
            assertThat(sheet.getRow(9).getCell(4).getNumericCellValue()).isEqualTo(10d);
            assertThat(sheet.getRow(10).getCell(4).getNumericCellValue()).isEqualTo(10d);
            assertThat(sheet.getMergedRegions()).extracting(region -> region.formatAsString())
                    .contains("B10:B11")
                    .doesNotContain("B10:B12");
        }
    }

    private SalesPiItem item(Long id, String productModel, String factoryModel, String quantity) {
        SalesPiItem item = new SalesPiItem();
        item.setId(id);
        item.setProductModel(productModel);
        item.setFactoryModel(factoryModel);
        item.setQty(new BigDecimal(quantity));
        return item;
    }

    private ShippingInstructionItem instructionItem(String productModel, String factoryModel, String quantity) {
        ShippingInstructionItem item = new ShippingInstructionItem();
        item.setProductModel(productModel);
        item.setFactoryModel(factoryModel);
        item.setQuantity(new BigDecimal(quantity));
        item.setCartonCount(new BigDecimal(quantity));
        return item;
    }

    private ShippingInstruction instruction() {
        ShippingInstruction instruction = new ShippingInstruction();
        instruction.setSiNo("SI-20260722-001");
        instruction.setPiId(10L);
        instruction.setCustomerId(20L);
        instruction.setCustomerCode("RU1001");
        instruction.setShipDate(LocalDate.of(2026, 7, 22));
        instruction.setShippingAddress("Shenzhen warehouse");
        instruction.setDeliveryAddress("Moscow warehouse");
        instruction.setContactName("Ilya");
        instruction.setPhone("+7 918 180 9258");
        instruction.setOrderNo("XF20260617PVZ7");
        instruction.setOrderCountry("俄罗斯");
        instruction.setCartonCount(new BigDecimal("15"));
        instruction.setOrderRequirement("加贴客户标签");
        instruction.setOwnerName("Alice");
        instruction.setOrderDate(LocalDate.of(2026, 6, 17));
        instruction.setRemark("优先安排发货");
        return instruction;
    }

    private void writePreviewIfRequested(Workbook workbook) throws Exception {
        String outputPath = System.getProperty("si.preview.path");
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        Path path = Path.of(outputPath);
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (OutputStream output = Files.newOutputStream(path)) {
            workbook.write(output);
        }
    }
}
