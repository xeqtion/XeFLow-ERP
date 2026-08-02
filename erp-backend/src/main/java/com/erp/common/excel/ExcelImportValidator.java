package com.erp.common.excel;

import com.erp.common.api.ResultCode;
import com.erp.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Locale;

/** Shared validation for template-based Excel imports. */
public final class ExcelImportValidator {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    public static final int MAX_ROWS = 5000;

    private ExcelImportValidator() {
    }

    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationError("导入文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw validationError("导入文件不能超过 20MB");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw validationError("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
        }
    }

    public static void validateRowCount(int rowCount) {
        if (rowCount > MAX_ROWS) {
            throw validationError("单次最多导入 " + MAX_ROWS + " 行，请拆分文件后重试");
        }
    }

    public static void validateText(String fieldName, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw validationError(fieldName + "不能超过 " + maxLength + " 个字符");
        }
    }

    public static void validateDecimal(String fieldName, BigDecimal value, int integerDigits, int scale) {
        if (value == null) {
            return;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int actualScale = Math.max(0, normalized.scale());
        int actualIntegerDigits = Math.max(0, normalized.precision() - normalized.scale());
        if (actualIntegerDigits > integerDigits || actualScale > scale) {
            throw validationError(fieldName + "最多支持 " + integerDigits + " 位整数和 " + scale + " 位小数");
        }
    }

    private static BusinessException validationError(String message) {
        return new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
}
