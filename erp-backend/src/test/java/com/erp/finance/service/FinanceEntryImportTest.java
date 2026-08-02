package com.erp.finance.service;

import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.excel.ImportResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceEntryImportTest {

    @Test
    void reportsUnknownCustomerAsRowErrorInsteadOfAbortingTheImport() throws Exception {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        AccountingCurrencyService currencyService = mock(AccountingCurrencyService.class);
        when(customerMapper.selectIdByCode("NO-SUCH-CUSTOMER")).thenReturn(null);
        FinanceEntryServiceImpl service = new FinanceEntryServiceImpl(customerMapper, currencyService);

        ImportResult result = service.importExcel(workbook());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFail()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement()
                .extracting(ImportResult.RowError::getMessage)
                .asString()
                .contains("客户ID不存在");
    }

    private MockMultipartFile workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("finance");
            String[] headers = {
                    "流水号",
                    "类型*(RECEIVABLE/PAYABLE/PRE_RECEIVE/PRE_PAY/UNRECEIVED/UNPAID)",
                    "方向(in收/out付,可空自动)",
                    "客户ID",
                    "金额*",
                    "币种",
                    "关联单据类型",
                    "关联单据号",
                    "入账日期",
                    "备注"
            };
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("SK-IMPORT-TEST");
            row.createCell(1).setCellValue("RECEIVABLE");
            row.createCell(3).setCellValue("NO-SUCH-CUSTOMER");
            row.createCell(4).setCellValue(100);
            row.createCell(5).setCellValue("CNY");
            workbook.write(out);
            return new MockMultipartFile("file", "finance.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
