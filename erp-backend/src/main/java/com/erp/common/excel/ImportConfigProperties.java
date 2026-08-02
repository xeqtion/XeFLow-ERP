package com.erp.common.excel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Excel 导入导出业务规则配置（前缀 erp.import）。
 *
 * <p>允许通过 application.yml 动态调整默认币种、列名映射、批量阈值等。</p>
 */
@Component
@ConfigurationProperties(prefix = "erp.import")
public class ImportConfigProperties {

    /** 默认币种 */
    private String defaultCurrency = "USD";

    /** 产品导入默认币种 */
    private String productDefaultCurrency = "CNY";

    /** 批量插入阈值（达到此数量时 flush 一次） */
    private int batchSize = 1000;

    /** 是否启用导入幂等控制（按 productId+currency 去重） */
    private boolean idempotentEnabled = true;

    /** 幂等策略: skip（跳过已有）, overwrite（更新已有） */
    private String idempotentStrategy = "overwrite";

    /** 列名映射表（key: 原始表头包含的关键字, value: 字段名） */
    private Map<String, String> columnAliases = new LinkedHashMap<>();

    /** 智能导入时是否忽略空格 */
    private boolean trimWhitespace = true;

    /** 智能导入时是否忽略大小写 */
    private boolean ignoreCase = true;

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }

    public String getProductDefaultCurrency() { return productDefaultCurrency; }
    public void setProductDefaultCurrency(String productDefaultCurrency) { this.productDefaultCurrency = productDefaultCurrency; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public boolean isIdempotentEnabled() { return idempotentEnabled; }
    public void setIdempotentEnabled(boolean idempotentEnabled) { this.idempotentEnabled = idempotentEnabled; }

    public String getIdempotentStrategy() { return idempotentStrategy; }
    public void setIdempotentStrategy(String idempotentStrategy) { this.idempotentStrategy = idempotentStrategy; }

    public Map<String, String> getColumnAliases() { return columnAliases; }
    public void setColumnAliases(Map<String, String> columnAliases) { this.columnAliases = columnAliases; }

    public boolean isTrimWhitespace() { return trimWhitespace; }
    public void setTrimWhitespace(boolean trimWhitespace) { this.trimWhitespace = trimWhitespace; }

    public boolean isIgnoreCase() { return ignoreCase; }
    public void setIgnoreCase(boolean ignoreCase) { this.ignoreCase = ignoreCase; }

    // ===================== 便捷方法 =====================

    /** 获取幂等策略枚举 */
    public IdempotentStrategy getEffectiveStrategy() {
        try {
            return IdempotentStrategy.valueOf(idempotentStrategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IdempotentStrategy.SKIP;
        }
    }

    public enum IdempotentStrategy {
        /** 跳过已有记录 */
        SKIP,
        /** 覆盖（更新）已有记录 */
        OVERWRITE
    }
}
