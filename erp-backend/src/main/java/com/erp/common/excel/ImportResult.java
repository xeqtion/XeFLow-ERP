package com.erp.common.excel;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入结果 —— 包含行级别状态明细与业务字段，前端可按状态颜色展示。
 */
public class ImportResult {

    /** 总行数 */
    private int total;

    /** 成功行数 */
    private int success;

    /** 失败行数 */
    private int fail;

    /** 警告行数 */
    private int warning;

    /** 行级明细 */
    private List<RowDetail> details = new ArrayList<>();

    /** 错误明细（向后兼容） */
    private List<RowError> errors = new ArrayList<>();

    // ===================== 带业务字段的方法 =====================

    public void addError(int rowIndex, String message) {
        addError(rowIndex, message, null, null);
    }

    public void addError(int rowIndex, String message, String factoryModel, String productName) {
        errors.add(new RowError(rowIndex, message));
        fail++;
        details.add(new RowDetail(rowIndex, message, RowStatus.ERROR, factoryModel, productName));
    }

    public void addSuccess(int rowIndex) {
        addSuccess(rowIndex, null, null);
    }

    public void addSuccess(int rowIndex, String factoryModel, String productName) {
        success++;
        details.add(new RowDetail(rowIndex, null, RowStatus.SUCCESS, factoryModel, productName));
    }

    /** 向后兼容：无行号的 addSuccess */
    public void addSuccess() {
        success++;
    }

    public void addWarning(int rowIndex, String message) {
        addWarning(rowIndex, message, null, null);
    }

    public void addWarning(int rowIndex, String message, String factoryModel, String productName) {
        warning++;
        details.add(new RowDetail(rowIndex, message, RowStatus.WARNING, factoryModel, productName));
    }

    // ---- getters / setters ----

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }

    public int getFail() { return fail; }
    public void setFail(int fail) { this.fail = fail; }

    public int getWarning() { return warning; }
    public void setWarning(int warning) { this.warning = warning; }

    public List<RowDetail> getDetails() { return details; }
    public void setDetails(List<RowDetail> details) { this.details = details; }

    public List<RowError> getErrors() { return errors; }
    public void setErrors(List<RowError> errors) { this.errors = errors; }

    // ---- inner types ----

    /** 行状态 */
    public enum RowStatus {
        SUCCESS, WARNING, ERROR
    }

    /** 行级明细（含业务字段供前端展示） */
    public static class RowDetail {
        private int row;
        private String message;
        private RowStatus status;
        /** 工厂产品型号 */
        private String factoryModel;
        /** 产品名称 */
        private String productName;

        public RowDetail() {}

        public RowDetail(int row, String message, RowStatus status, String factoryModel, String productName) {
            this.row = row;
            this.message = message;
            this.status = status;
            this.factoryModel = factoryModel;
            this.productName = productName;
        }

        public int getRow() { return row; }
        public void setRow(int row) { this.row = row; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public RowStatus getStatus() { return status; }
        public void setStatus(RowStatus status) { this.status = status; }

        public String getFactoryModel() { return factoryModel; }
        public void setFactoryModel(String factoryModel) { this.factoryModel = factoryModel; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
    }

    /** 错误行（向后兼容） */
    public static class RowError {
        private final int row;
        private final String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }

        public int getRow() { return row; }
        public String getMessage() { return message; }
    }
}
