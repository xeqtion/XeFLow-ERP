package com.erp.basedata.service;

import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartProductImportServiceTest {

    @Test
    void importsAliasHeadersAndKeepsGeneratedCodeWithinDatabaseLimit() throws Exception {
        ProductMapper mapper = mock(ProductMapper.class);
        when(mapper.selectNextProductId()).thenReturn(100000L);
        when(mapper.selectByFactoryModelList(anyList())).thenReturn(List.of());
        when(mapper.insert(any(Product.class))).thenReturn(1);
        SmartProductImportService service = new SmartProductImportService(mapper);

        String longFactoryModel = "M".repeat(70);
        MockMultipartFile file = workbook(
                new String[]{"品名", "工厂型号", "出厂价", "币种"},
                new Object[]{"Alias Product", longFactoryModel, 12.5, "CNY"});

        ImportResult result = service.compatImport(file);

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFail()).isZero();
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(mapper).insert(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getProductName()).isEqualTo("Alias Product");
        assertThat(saved.getFactoryModel()).isEqualTo(longFactoryModel);
        assertThat(saved.getFactoryPrice()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(saved.getProdCode()).hasSizeLessThanOrEqualTo(64).endsWith("-100000");
    }

    private MockMultipartFile workbook(String[] headers, Object[] values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("products");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            var row = sheet.createRow(1);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Number number) {
                    row.createCell(i).setCellValue(number.doubleValue());
                } else {
                    row.createCell(i).setCellValue(String.valueOf(value));
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
