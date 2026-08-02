package com.erp.basedata.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.erp.basedata.dto.ProductExcel;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportResult;
import com.erp.common.excel.ExcelImportValidator;
import com.erp.common.security.SecurityUtils;
import com.erp.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SmartProductImportService {

    private static final Logger log = LoggerFactory.getLogger(SmartProductImportService.class);

    private final ProductMapper productMapper;

    public SmartProductImportService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    private static final Map<String, String> COLUMN_MAP = buildColumnMap();

    private static Map<String, String> buildColumnMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("产品名称", "productName");
        map.put("品名", "productName");
        map.put("产品名", "productName");
        map.put("产品ID", "externalId");
        map.put("产品编号", "externalId");
        map.put("工厂产品型号", "factoryModel");
        map.put("工厂型号", "factoryModel");
        map.put("高宇型号", "companyModel");
        map.put("公司产品型号", "companyModel");
        map.put("公司型号", "companyModel");
        map.put("出厂价", "factoryPrice");
        map.put("高义出厂价", "factoryPrice");
        map.put("出厂单价", "factoryPrice");
        map.put("标准价", "factoryPrice");
        map.put("产品备注", "remark");
        map.put("备注", "remark");
        map.put("产品说明", "remark");
        map.put("币种", "currency");
        map.put("货币", "currency");
        return Collections.unmodifiableMap(map);
    }

    // ========================================================================
    //  API
    // ========================================================================

    @Transactional(rollbackFor = Exception.class)
    public ImportResult compatImport(MultipartFile file) throws IOException {
        ExcelImportValidator.validateFile(file);
        List<ProductExcel> exactRows = readWithExactDto(file);
        ExcelImportValidator.validateRowCount(exactRows.size());
        if (exactRows.isEmpty()) {
            ImportResult empty = new ImportResult();
            empty.setTotal(0);
            return empty;
        }
        boolean hasValidData = exactRows.stream().anyMatch(r ->
                r.getProductName() != null || r.getFactoryModel() != null);

        if (hasValidData) {
            log.info("精确匹配成功，行数: {}", exactRows.size());
            return saveFromExact(exactRows);
        }

        log.info("精确匹配无数据，回退到别名导入");
        return smartImport(file);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult smartImport(MultipartFile file) throws IOException {
        List<Map<Integer, String>> allRows = readAllRows(file);
        if (allRows.isEmpty()) {
            ImportResult result = new ImportResult();
            result.addError(0, "无法读取 Excel 内容", null, null);
            return result;
        }

        Map<Integer, String> colToField = buildColumnMapping(allRows.get(0));
        if (colToField.isEmpty()) {
            ImportResult result = new ImportResult();
            result.addError(0, "未识别到任何已知列", null, null);
            return result;
        }

        ImportResult result = new ImportResult();
        int dataRowCount = allRows.size() - 1;
        ExcelImportValidator.validateRowCount(dataRowCount);
        result.setTotal(dataRowCount);

        List<ImportRow> importRows = new ArrayList<>();
        long nextId = productMapper.selectNextProductId();

        for (int i = 1; i < allRows.size(); i++) {
            Map<Integer, String> rawRow = allRows.get(i);
            int dataRowNo = i; // 0-based without header
            try {
                Product product = mapToProduct(rawRow, colToField);
                if (product == null) {
                    result.addWarning(dataRowNo, "跳过空行（缺少产品名称和工厂型号）",
                            extractField(rawRow, colToField, "factoryModel"),
                            extractField(rawRow, colToField, "productName"));
                    continue;
                }
                product.setId(nextId++);
                prepareForSave(product);
                importRows.add(new ImportRow(product, dataRowNo, product.getFactoryModel(),
                        product.getProductName(), extractField(rawRow, colToField, "externalId")));
            } catch (Exception e) {
                log.warn("行转换失败: {}", e.getMessage());
                result.addError(dataRowNo, "行转换失败: " + e.getMessage(),
                        extractField(rawRow, colToField, "factoryModel"),
                        extractField(rawRow, colToField, "productName"));
            }
        }

        doUpsert(importRows, result);
        return result;
    }

    // ========================================================================
    //  精确匹配模式
    // ========================================================================

    private List<ProductExcel> readWithExactDto(MultipartFile file) throws IOException {
        List<ProductExcel> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), ProductExcel.class,
                new ReadListener<ProductExcel>() {
                    @Override public void invoke(ProductExcel row, AnalysisContext ctx) { rows.add(row); }
                    @Override public void doAfterAllAnalysed(AnalysisContext ctx) {}
                }).sheet().doRead();
        return rows;
    }

    private ImportResult saveFromExact(List<ProductExcel> rows) {
        ImportResult result = new ImportResult();
        result.setTotal(rows.size());

        List<ImportRow> importRows = new ArrayList<>();
        long nextId = productMapper.selectNextProductId();

        for (int i = 0; i < rows.size(); i++) {
            ProductExcel row = rows.get(i);
            int dataRowNo = i + 1;

            // 过滤空行：名称和型号都为空 → 跳过
            boolean emptyRow = (row.getProductName() == null || row.getProductName().isBlank())
                    && (row.getFactoryModel() == null || row.getFactoryModel().isBlank());
            if (emptyRow) {
                result.addWarning(dataRowNo, "跳过空行（缺少产品名称和工厂型号）", null, null);
                continue;
            }

            try {
                Product product = new Product();
                BeanUtils.copyProperties(row, product);
                product.setId(nextId++);
                prepareForSave(product);
                importRows.add(new ImportRow(product, dataRowNo,
                        product.getFactoryModel(), product.getProductName(), normalizeText(row.getId())));
            } catch (BusinessException e) {
                result.addError(dataRowNo, e.getMessage(), row.getFactoryModel(), row.getProductName());
            }
        }

        doUpsert(importRows, result);
        return result;
    }

    // ========================================================================
    //  Upsert 核心逻辑
    // ========================================================================

    private void doUpsert(List<ImportRow> importRows, ImportResult result) {
        if (importRows.isEmpty()) return;

        importRows = deduplicateFactoryModels(importRows, result);

        // 1. 批量预查：按 factoryModel 查
        Set<String> factoryModels = importRows.stream()
                .map(r -> r.product.getFactoryModel())
                .filter(Objects::nonNull)
                .filter(m -> !m.isBlank())
                .collect(Collectors.toSet());

        Map<String, Product> factoryModelMap = new HashMap<>();
        if (!factoryModels.isEmpty()) {
            List<Product> found = productMapper.selectByFactoryModelList(new ArrayList<>(factoryModels));
            for (Product p : found) {
                String key = normalizeKey(p.getFactoryModel());
                if (key != null) {
                    factoryModelMap.put(key, p);
                }
            }
        }

        // 2. 收集那些 factoryModel 为空的行的 productName，用于二次查找
        Set<String> fallbackNames = importRows.stream()
                .filter(r -> r.product.getFactoryModel() == null || r.product.getFactoryModel().isBlank())
                .map(r -> r.product.getProductName())
                .filter(Objects::nonNull)
                .filter(n -> !n.isBlank())
                .collect(Collectors.toSet());

        Map<String, Product> nameMap = new HashMap<>();
        if (!fallbackNames.isEmpty()) {
            for (String name : fallbackNames) {
                List<Product> byName = productMapper.selectByName(name);
                if (byName.size() == 1) {
                    nameMap.put(name, byName.get(0));
                }
                // 多个同名产品不自动匹配（歧义，留给人工处理）
            }
        }

        // 3. 逐行决定 upsert
        List<ImportRow> toInsert = new ArrayList<>();
        List<ImportRow> toUpdate = new ArrayList<>();

        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        for (ImportRow row : importRows) {
            Product product = row.product;

            // 先按 factoryModel 查找
            Product exist = null;
            String fm = product.getFactoryModel();
            if (fm != null && !fm.isBlank()) {
                exist = factoryModelMap.get(normalizeKey(fm));
            }

            // factoryModel 为空或未命中，按产品名称回退
            if (exist == null && product.getProductName() != null) {
                Product nameMatch = nameMap.get(product.getProductName());
                if (nameMatch != null) {
                    exist = nameMatch;
                }
            }

            if (exist != null) {
                // 更新已有记录
                product.setId(exist.getId());
                product.setCreateBy(exist.getCreateBy());
                product.setCreateTime(exist.getCreateTime());
                product.setUpdateBy(userId);
                product.setUpdateTime(now);
                product.setProdCode(exist.getProdCode() != null
                        ? exist.getProdCode()
                        : buildGeneratedProductCode(product));
                toUpdate.add(row);
            } else if ((fm == null || fm.isBlank()) && product.getProductName() != null) {
                // 工厂型号为空且按名称也未匹配到 -> 无足够信息创建产品，拒绝
                result.addError(row.rowNo, "工厂型号为空，且未匹配到已有的同名产品，无法创建",
                        null, product.getProductName());
            } else {
                // 正常新增
                product.setCreateBy(userId);
                product.setCreateTime(now);
                product.setUpdateBy(userId);
                product.setUpdateTime(now);
                toInsert.add(row);
            }
        }

        // 4. 执行
        for (ImportRow row : toInsert) {
            insertWithIdRetry(row.product);
            if (row.externalId != null) {
                result.addWarning(row.rowNo,
                        "Excel 产品ID " + row.externalId + " 未作为数据库主键，系统已分配 " + row.product.getId(),
                        row.factoryModel, row.productName);
            } else {
                result.addSuccess(row.rowNo, row.factoryModel, row.productName);
            }
        }
        for (ImportRow row : toUpdate) {
            productMapper.updateById(row.product);
            String message = "覆盖已有记录 (系统ID=" + row.product.getId() + ")";
            if (row.externalId != null) {
                message += "；Excel 产品ID " + row.externalId + " 已忽略";
            }
            result.addWarning(row.rowNo, message,
                    row.factoryModel != null ? row.factoryModel : row.productName, row.productName);
        }
    }

    private List<ImportRow> deduplicateFactoryModels(List<ImportRow> rows, ImportResult result) {
        List<ImportRow> deduplicated = new ArrayList<>();
        Map<String, Integer> indexByModel = new LinkedHashMap<>();
        for (ImportRow row : rows) {
            String key = normalizeKey(row.factoryModel);
            if (key == null) {
                deduplicated.add(row);
                continue;
            }
            Integer previousIndex = indexByModel.putIfAbsent(key, deduplicated.size());
            if (previousIndex == null) {
                deduplicated.add(row);
                continue;
            }
            ImportRow previous = deduplicated.set(previousIndex, row);
            result.addWarning(previous.rowNo,
                    "文件内工厂型号重复，已由第 " + row.rowNo + " 行覆盖",
                    previous.factoryModel, previous.productName);
        }
        return deduplicated;
    }

    private void insertWithIdRetry(Product product) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                productMapper.insert(product);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 3) {
                    throw e;
                }
                Long nextId = productMapper.selectNextProductId();
                product.setId(nextId);
                product.setProdCode(buildGeneratedProductCode(product));
            }
        }
    }

    private String buildGeneratedProductCode(Product product) {
        String model = normalizeText(product.getFactoryModel());
        String id = String.valueOf(product.getId());
        if (model == null) {
            return id;
        }
        String suffix = "-" + id;
        int maxPrefixLength = Math.max(0, 64 - suffix.length());
        String prefix = model.length() > maxPrefixLength ? model.substring(0, maxPrefixLength) : model;
        return prefix + suffix;
    }

    private String normalizeKey(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ========================================================================
    //  列映射构建
    // ========================================================================

    private Map<Integer, String> buildColumnMapping(Map<Integer, String> headerRow) {
        Map<Integer, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            String header = headerRow.get(i);
            if (header == null || header.isBlank()) continue;
            String field = COLUMN_MAP.get(header.trim());
            if (field == null) {
                field = resolveAlias(header.trim());
            }
            if (field != null) {
                mapping.put(i, field);
                log.debug("列 {} [{}] → {}", i, header, field);
            } else {
                log.debug("列 {} [{}] → 跳过", i, header);
            }
        }
        return mapping;
    }

    private String resolveAlias(String header) {
        for (Map.Entry<String, String> e : COLUMN_MAP.entrySet()) {
            if (header.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    // ========================================================================
    //  读取
    // ========================================================================

    private List<Map<Integer, String>> readAllRows(MultipartFile file) throws IOException {
        List<Map<Integer, String>> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), new ReadListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                rows.add(new HashMap<>(row));
            }
            @Override public void doAfterAllAnalysed(AnalysisContext ctx) {}
        }).headRowNumber(0).sheet().doRead();
        while (!rows.isEmpty() && isRowEmpty(rows.get(rows.size() - 1))) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }

    private boolean isRowEmpty(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) return true;
        for (String v : row.values()) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    // ========================================================================
    //  转换
    // ========================================================================

    private Product mapToProduct(Map<Integer, String> rawRow, Map<Integer, String> colToField) {
        Product product = new Product();
        for (Map.Entry<Integer, String> e : colToField.entrySet()) {
            String value = rawRow.get(e.getKey());
            if (value == null || value.isBlank()) continue;
            setField(product, e.getValue(), value.trim());
        }
        // 必须有名称或型号
        boolean hasName = product.getProductName() != null && !product.getProductName().isBlank();
        boolean hasModel = product.getFactoryModel() != null && !product.getFactoryModel().isBlank();
        if (!hasName && !hasModel) {
            return null;
        }
        return product;
    }

    private void setField(Product product, String field, String value) {
        switch (field) {
            case "productName" -> product.setProductName(value);
            case "factoryModel" -> product.setFactoryModel(value);
            case "companyModel" -> product.setCompanyModel(value);
            case "remark" -> product.setRemark(value);
            case "currency" -> product.setCurrency(value);
            case "externalId" -> { /* 外部参考ID不写入数据库主键 */ }
            case "factoryPrice" -> {
                try {
                    product.setFactoryPrice(new BigDecimal(value.replaceAll("[^\\d.\\-]", "")));
                } catch (NumberFormatException e) {
                    throw new BusinessException("出厂价格式不正确: " + value);
                }
            }
            default -> log.debug("未处理字段: {}", field);
        }
    }

    private String extractField(Map<Integer, String> rawRow, Map<Integer, String> colToField, String fieldName) {
        for (Map.Entry<Integer, String> e : colToField.entrySet()) {
            if (e.getValue().equals(fieldName)) {
                return rawRow.get(e.getKey());
            }
        }
        return null;
    }

    private void prepareForSave(Product product) {
        if (product.getProductName() == null) {
            product.setProductName(product.getProdNameCn() != null ? product.getProdNameCn() : product.getProdNameEn());
        }
        if (product.getCompanyModel() == null) {
            product.setCompanyModel(product.getSpec());
        }
        if (product.getFactoryModel() == null && product.getProdCode() != null) {
            product.setFactoryModel(product.getProdCode());
        }
        if (product.getFactoryPrice() == null) {
            product.setFactoryPrice(product.getCostPrice());
        }
        if (product.getCurrency() == null) {
            product.setCurrency("CNY");
        }
        if (product.getProdCode() == null && product.getId() != null) {
            product.setProdCode(buildGeneratedProductCode(product));
        }
        ExcelImportValidator.validateText("产品代码", product.getProdCode(), 64);
        ExcelImportValidator.validateText("产品名称", product.getProductName(), 255);
        ExcelImportValidator.validateText("公司产品型号", product.getCompanyModel(), 255);
        ExcelImportValidator.validateText("工厂产品型号", product.getFactoryModel(), 255);
        ExcelImportValidator.validateText("币种", product.getCurrency(), 8);
        ExcelImportValidator.validateText("备注", product.getRemark(), 512);
        ExcelImportValidator.validateDecimal("出厂价", product.getFactoryPrice(), 14, 4);
    }

    // ========================================================================
    //  内部数据结构
    // ========================================================================

    private static class ImportRow {
        final Product product;
        final int rowNo;
        final String factoryModel;
        final String productName;
        final String externalId;

        ImportRow(Product product, int rowNo, String factoryModel, String productName, String externalId) {
            this.product = product;
            this.rowNo = rowNo;
            this.factoryModel = factoryModel;
            this.productName = productName;
            this.externalId = externalId;
        }
    }
}
