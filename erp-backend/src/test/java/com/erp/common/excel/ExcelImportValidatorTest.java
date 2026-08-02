package com.erp.common.excel;

import com.erp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImportValidatorTest {

    @Test
    void acceptsSupportedExcelFilesAndDatabaseSafeValues() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.xlsx", "application/octet-stream", new byte[]{1});

        assertThatCode(() -> ExcelImportValidator.validateFile(file)).doesNotThrowAnyException();
        assertThatCode(() -> ExcelImportValidator.validateText("备注", "ok", 512)).doesNotThrowAnyException();
        assertThatCode(() -> ExcelImportValidator.validateDecimal(
                "金额", new BigDecimal("99999999999999.9999"), 14, 4)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedFilesOversizedRowsAndUnsafeDecimals() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv", new byte[]{1});

        assertThatThrownBy(() -> ExcelImportValidator.validateFile(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> ExcelImportValidator.validateRowCount(ExcelImportValidator.MAX_ROWS + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5000");
        assertThatThrownBy(() -> ExcelImportValidator.validateDecimal(
                "金额", new BigDecimal("100000000000000.0000"), 14, 4))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("14 位整数");
    }
}
