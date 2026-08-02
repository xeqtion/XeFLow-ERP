package com.erp.sales.service;

import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportConfigProperties;
import com.erp.common.excel.ImportResult;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.mapper.StandardPriceMapper;
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

class SmartStandardPriceImportServiceTest {

    @Test
    void importsAliasHeadersAndAssignsIdsForBatchInsert() throws Exception {
        StandardPriceMapper priceMapper = mock(StandardPriceMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ImportConfigProperties config = new ImportConfigProperties();
        config.setBatchSize(100);

        Product product = new Product();
        product.setId(100000L);
        product.setProdCode("P100000");
        product.setProductName("Alias Product");
        product.setCompanyModel("CM-1");
        product.setFactoryModel("FM-1");
        when(productMapper.selectByFactoryModel("FM-1")).thenReturn(product);
        when(priceMapper.selectList(any())).thenReturn(List.of());
        when(priceMapper.insertBatch(anyList())).thenReturn(1);

        SmartStandardPriceImportService service =
                new SmartStandardPriceImportService(priceMapper, productMapper, config);
        MockMultipartFile file = workbook();

        ImportResult result = service.compatImport(file);

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFail()).isZero();
        ArgumentCaptor<List<StandardPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceMapper).insertBatch(captor.capture());
        StandardPrice saved = captor.getValue().get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProductId()).isEqualTo(100000L);
        assertThat(saved.getStandardUnitPrice()).isEqualByComparingTo(new BigDecimal("12.3456"));
    }

    private MockMultipartFile workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("prices");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("工厂产品型号");
            header.createCell(1).setCellValue("标准单价");
            header.createCell(2).setCellValue("币种");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("FM-1");
            row.createCell(1).setCellValue(12.3456);
            row.createCell(2).setCellValue("USD");
            workbook.write(out);
            return new MockMultipartFile("file", "prices.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
