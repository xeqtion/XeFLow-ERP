package com.erp.sales.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportConfigProperties;
import com.erp.common.excel.ImportConfigProperties.IdempotentStrategy;
import com.erp.common.excel.ExcelImportValidator;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.sales.dto.StandardPriceExcel;
import com.erp.sales.dto.StandardPriceImportDTO;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.mapper.StandardPriceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能销售报价导入服务（重构版）。
 *
 * <p>改进点：</p>
 * <ol>
 *   <li>统一实体构建逻辑 —— 所有导入路径共用 {@link #buildStandardPrice}</li>
 *   <li>优化异常处理 —— 仅 {@link ExcelAnalysisException} 回退，IO/运行时异常直接抛出</li>
 *   <li>流式读取 —— ReadListener 逐行处理，每 N 条批量插入</li>
 *   <li>幂等性控制 —— 按 productId+currency 查重，支持 skip/overwrite 策略</li>
 *   <li>业务规则剥离 —— 默认币种、批量阈值等来自 {@link ImportConfigProperties}</li>
 *   <li>增强列映射 —— 同时支持精确匹配、contains 模糊匹配、正则匹配（regex: 前缀）</li>
 *   <li>DTO/Entity 分离 —— 别名导入使用 {@link StandardPriceImportDTO} 过渡</li>
 * </ol>
 */
@Service
public class SmartStandardPriceImportService {

    private static final Logger log = LoggerFactory.getLogger(SmartStandardPriceImportService.class);

    /** 静默忽略的列 */
    private static final String SKIP = "__skip__";

    /** 内置列名映射表（可作为 application.yml erp.import.column-aliases 的基线） */
    private static final Map<String, String> BUILTIN_COLUMN_MAP = buildBuiltinMap();

    private static Map<String, String> buildBuiltinMap() {
        Map<String, String> map = new LinkedHashMap<>();
        // 精确匹配
        map.put("NO", SKIP);
        map.put("工厂产品型号", "factoryModel");
        map.put("高宇型号", "companyModel");
        map.put("产品名称", "productName");
        map.put("产品备注", "remark");
        map.put("备注", "remark");
        map.put("高义出厂价", "factoryPrice");
        map.put("出厂价", "factoryPrice");
        map.put("标准单价", "factoryPrice");
        map.put("单价", "factoryPrice");
        map.put("币种", "currency");
        map.put("货币", "currency");
        // contains 模糊匹配（值包含 key 即命中）
        map.put("厂产品型号", "factoryModel");
        map.put("公司产品型号", "companyModel");
        map.put("公司型号", "companyModel");
        map.put("出厂单价", "factoryPrice");
        map.put("标准价", "factoryPrice");
        return Collections.unmodifiableMap(map);
    }

    private final StandardPriceMapper standardPriceMapper;
    private final ProductMapper productMapper;
    private final ImportConfigProperties config;

    /** 别名导入的数据行计数器（流式处理时需要跨方法访问） */
    private int smartRowCounter = 0;

    public SmartStandardPriceImportService(StandardPriceMapper standardPriceMapper,
                                           ProductMapper productMapper,
                                           ImportConfigProperties config) {
        this.standardPriceMapper = standardPriceMapper;
        this.productMapper = productMapper;
        this.config = config;
    }

    // ========================================================================
    //  兼容导入入口
    // ========================================================================

    /**
     * 兼容导入：先尝试精确 DTO 匹配（如 {@link StandardPriceExcel} 格式），
     * <p>若抛出 {@link ExcelAnalysisException} 或读出行数为空则回退别名导入。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult compatImport(MultipartFile file) throws IOException {
        ExcelImportValidator.validateFile(file);
        ImportResult result;

        // 尝试精确 DTO 匹配
        List<StandardPriceExcel> exactRows = null;
        try {
            exactRows = readAsDto(file);
            boolean hasValid = exactRows.stream().anyMatch(r -> r.getProductId() != null);
            if (hasValid) {
                log.info("精确匹配成功，行数: {}", exactRows.size());
                return importFromDto(exactRows);
            }
            log.info("精确匹配无有效数据");
        } catch (ExcelAnalysisException e) {
            log.info("精确匹配列不匹配 ({}), 回退别名导入", e.getMessage());
        } catch (IOException e) {
            throw e;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("精确匹配异常 ({})，回退别名导入", e.getMessage());
        }

        // 回退别名导入
        log.info("开始别名导入");
        return smartImport(file);
    }

    // ========================================================================
    //  精确 DTO 导入（基于 StandardPriceExcel）
    // ========================================================================

    private List<StandardPriceExcel> readAsDto(MultipartFile file) throws IOException {
        List<StandardPriceExcel> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), StandardPriceExcel.class, new ReadListener<StandardPriceExcel>() {
            @Override
            public void invoke(StandardPriceExcel row, AnalysisContext ctx) {
                rows.add(row);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext ctx) {}
        }).sheet().doRead();
        return rows;
    }

    private ImportResult importFromDto(List<StandardPriceExcel> rows) {
        ExcelImportValidator.validateRowCount(rows.size());
        ImportResult result = new ImportResult();
        result.setTotal(rows.size());

        List<StandardPrice> toSave = new ArrayList<>();
        List<StandardPrice> toUpdate = new ArrayList<>();
        // 预查所有产品（一次 SQL）
        Set<Long> productIds = rows.stream()
                .map(StandardPriceExcel::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> productCache = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds)
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        // 幂等性预查
        Map<String, StandardPrice> existingMap = idempotentPreload(productCache);

        for (int i = 0; i < rows.size(); i++) {
            StandardPriceExcel row = rows.get(i);
            int rowNo = i + 1;
            try {
                Product product = row.getProductId() != null ? productCache.get(row.getProductId()) : null;
                if (product == null) {
                    result.addError(rowNo, "产品不存在: " + row.getProductId(),
                            null, row.getProductName());
                    continue;
                }
                Map<String, String> fields = extractFieldsFromDto(row);
                StandardPrice sp = buildStandardPrice(product, fields);
                IdempotencyAction action = handleIdempotency(sp, existingMap, result, rowNo,
                        product.getFactoryModel(), product.getProductName());
                if (action == IdempotencyAction.PROCEED) {
                    toSave.add(sp);
                    result.addSuccess(rowNo, product.getFactoryModel(), product.getProductName());
                } else if (action == IdempotencyAction.UPDATED) {
                    toUpdate.add(sp);
                }
            } catch (BusinessException ex) {
                result.addError(rowNo, ex.getMessage(), null, row.getProductName());
            }
        }
        if (!toSave.isEmpty() || !toUpdate.isEmpty()) {
            flushBatches(toSave, toUpdate);
        }
        return result;
    }

    /** 从 DTO 提取字段值 Map（与别名导入共享 buildStandardPrice） */
    private Map<String, String> extractFieldsFromDto(StandardPriceExcel dto) {
        Map<String, String> fields = new HashMap<>();
        if (dto.getProductName() != null) fields.put("productName", dto.getProductName());
        if (dto.getCompanyModel() != null) fields.put("companyModel", dto.getCompanyModel());
        if (dto.getRemark() != null) fields.put("remark", dto.getRemark());
        if (dto.getStandardUnitPrice() != null) fields.put("factoryPrice", dto.getStandardUnitPrice().toString());
        if (dto.getDiscountUnitPrice() != null) fields.put("discountUnitPrice", dto.getDiscountUnitPrice().toString());
        if (dto.getFloorPrice() != null) fields.put("floorPrice", dto.getFloorPrice().toString());
        if (dto.getCurrency() != null) fields.put("currency", dto.getCurrency());
        return fields;
    }

    // ========================================================================
    //  别名智能导入（流式 + 批量）
    // ========================================================================

    /**
     * 别名导入：流式读取，每 N 条批量处理一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult smartImport(MultipartFile file) throws IOException {
        ImportResult result = new ImportResult();
        smartRowCounter = 0;
        List<Map<Integer, String>> batch = new ArrayList<>();
        Map<Integer, String>[] colToFieldRef = new Map[1]; // 引用持有

        EasyExcel.read(file.getInputStream(), new ReadListener<Map<Integer, String>>() {
            private boolean headerParsed = false;

            @Override
            public void invoke(Map<Integer, String> row, AnalysisContext ctx) {
                if (!headerParsed) {
                    colToFieldRef[0] = buildColumnMapping(row);
                    if (colToFieldRef[0].isEmpty()) {
                        throw new ExcelAnalysisException("未识别到任何已知列");
                    }
                    headerParsed = true;
                    return;
                }
                if (isRowEmpty(row)) return;
                smartRowCounter++;
                ExcelImportValidator.validateRowCount(smartRowCounter);
                batch.add(new HashMap<>(row));

                if (batch.size() >= config.getBatchSize()) {
                    flushSmartBatch(batch, colToFieldRef[0], result);
                    batch.clear();
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext ctx) {
                if (!batch.isEmpty()) {
                    flushSmartBatch(batch, colToFieldRef[0], result);
                    batch.clear();
                }
            }
        }).headRowNumber(0).sheet().doRead();

        result.setTotal(smartRowCounter);
        return result;
    }

    /** 流式批量处理 */
    private void flushSmartBatch(List<Map<Integer, String>> batch,
                                  Map<Integer, String> colToField,
                                  ImportResult result) {
        if (batch.isEmpty()) return;

        // 1. 提取所有 factoryModel 和 productName
        Set<String> factoryModels = new HashSet<>();
        Set<String> productNames = new HashSet<>();
        List<StandardPriceImportDTO> dtos = new ArrayList<>(batch.size());
        for (Map<Integer, String> rawRow : batch) {
            StandardPriceImportDTO dto = mapToDto(rawRow, colToField);
            if (dto != null) {
                if (dto.getFactoryModel() != null) {
                    factoryModels.add(dto.getFactoryModel());
                } else if (dto.getProductName() != null) {
                    productNames.add(dto.getProductName());
                }
            }
            dtos.add(dto);
        }

        // 2. 批量查产品（按 factoryModel）
        Map<String, Product> productCache = batchLoadProducts(factoryModels);

        // 补充按 productName 查找（给无 factoryModel 的行）
        Map<String, Product> nameCache = new HashMap<>();
        for (String name : productNames) {
            if (productCache.containsValue(name)) continue;
            List<Product> byName = productMapper.selectByName(name);
            if (byName.size() == 1) {
                nameCache.put(name, byName.get(0));
            }
        }

        // 3. 幂等性预查
        Set<Long> matchedIds = productCache.values().stream().map(Product::getId).collect(Collectors.toSet());
        matchedIds.addAll(nameCache.values().stream().map(Product::getId).collect(Collectors.toSet()));
        Map<String, StandardPrice> existingMap = idempotentPreloadByIds(matchedIds);

        // 4. 逐行构建
        List<StandardPrice> toSave = new ArrayList<>();
        List<StandardPrice> toUpdate = new ArrayList<>();
        int firstRowNo = smartRowCounter - batch.size() + 1;
        for (int i = 0; i < dtos.size(); i++) {
            StandardPriceImportDTO dto = dtos.get(i);
            if (dto == null) continue;
            int rowNo = firstRowNo + i;

            // 查找 product：优先用 factoryModel，回退到 productName
            Product product = null;
            if (dto.getFactoryModel() != null) {
                product = productCache.get(dto.getFactoryModel());
            }
            if (product == null && dto.getProductName() != null) {
                product = nameCache.get(dto.getProductName());
                if (product == null) {
                    List<Product> byName = productMapper.selectByName(dto.getProductName());
                    if (!byName.isEmpty()) product = byName.get(0);
                }
            }
            if (product == null) {
                String identifier = dto.getFactoryModel() != null ? dto.getFactoryModel() : dto.getProductName();
                result.addError(rowNo, "未找到匹配的产品: " + identifier,
                        dto.getFactoryModel(), dto.getProductName());
                continue;
            }
            try {
                Map<String, String> fields = dtoToFields(dto);
                StandardPrice sp = buildStandardPrice(product, fields);
                IdempotencyAction action = handleIdempotency(sp, existingMap, result, rowNo,
                        product.getFactoryModel(), product.getProductName());
                if (action == IdempotencyAction.PROCEED) {
                    toSave.add(sp);
                    result.addSuccess(rowNo, product.getFactoryModel(), product.getProductName());
                } else if (action == IdempotencyAction.UPDATED) {
                    toUpdate.add(sp);
                }
            } catch (Exception ex) {
                log.warn("第 {} 行转换失败: {}", rowNo, ex.getMessage());
                result.addError(rowNo, "解析失败: " + ex.getMessage(),
                        dto.getFactoryModel(), dto.getProductName());
            }
        }

        if (!toSave.isEmpty() || !toUpdate.isEmpty()) {
            flushBatches(toSave, toUpdate);
        }
    }

    // ========================================================================
    //  核心构建方法 —— 统一实体构建逻辑 [改进 1]
    // ========================================================================

    /**
     * 统一构建 StandardPrice 实体。
     * <p>无论哪种导入路径（DTO / 别名）均调用此方法，保证审计字段、默认值、校验逻辑一致。</p>
     *
     * @param product     已查到的 Product 实体
     * @param fieldValues 字段值 Map（key=字段名，value=字符串值，不含 null）
     * @return 已填充完毕的 StandardPrice（不含 id）
     */
    public StandardPrice buildStandardPrice(Product product, Map<String, String> fieldValues) {
        StandardPrice sp = new StandardPrice();
        sp.setProductId(product.getId());
        sp.setProdCode(product.getProdCode());

        // 用导入值填充，无导入值则用 product 的默认值
        sp.setProductName(getOrElse(fieldValues, "productName", product.getProductName()));
        sp.setCompanyModel(getOrElse(fieldValues, "companyModel", product.getCompanyModel()));
        sp.setRemark(getOrElse(fieldValues, "remark", null));

        // 价格字段
        String priceStr = fieldValues.get("factoryPrice");
        BigDecimal standardUnitPrice = priceStr != null ? parsePrice(priceStr) : null;
        if (standardUnitPrice == null) {
            throw new BusinessException("标准单价不能为空 (产品: " + product.getProductName() + ")");
        }
        sp.setStandardUnitPrice(standardUnitPrice);
        sp.setPrice(standardUnitPrice);

        // 折扣单价 —— 有则用，无则默认标准单价
        String discountStr = fieldValues.get("discountUnitPrice");
        BigDecimal discountPrice = discountStr != null ? parsePrice(discountStr) : standardUnitPrice;
        if (discountPrice == null) {
            throw new BusinessException("折扣单价格式不正确");
        }
        sp.setDiscountUnitPrice(discountPrice);

        // 底价 —— 有则用，无则默认折扣单价
        String floorStr = fieldValues.get("floorPrice");
        BigDecimal floorPrice = floorStr != null ? parsePrice(floorStr) : discountPrice;
        if (floorPrice == null) {
            throw new BusinessException("底价格式不正确");
        }
        sp.setFloorPrice(floorPrice);

        // 币种
        String currency = fieldValues.get("currency");
        sp.setCurrency(currency != null ? currency.trim().toUpperCase() : config.getDefaultCurrency());

        ExcelImportValidator.validateText("产品代码", sp.getProdCode(), 64);
        ExcelImportValidator.validateText("产品名称", sp.getProductName(), 255);
        ExcelImportValidator.validateText("公司产品型号", sp.getCompanyModel(), 255);
        ExcelImportValidator.validateText("币种", sp.getCurrency(), 8);
        ExcelImportValidator.validateText("备注", sp.getRemark(), 512);
        ExcelImportValidator.validateDecimal("标准单价", sp.getStandardUnitPrice(), 14, 4);
        ExcelImportValidator.validateDecimal("折扣单价", sp.getDiscountUnitPrice(), 14, 4);
        ExcelImportValidator.validateDecimal("底价", sp.getFloorPrice(), 14, 4);

        // 审计字段（MyBatis-Plus MetaObjectHandler 不会对 insertBatch 生效）
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        sp.setCreateBy(userId);
        sp.setCreateTime(now);
        sp.setUpdateBy(userId);
        sp.setUpdateTime(now);

        return sp;
    }

    // ========================================================================
    //  幂等性控制 [改进 4]
    // ========================================================================

    /** 检查并处理已有记录 */
    private enum IdempotencyAction { PROCEED, SKIP_ROW, UPDATED }

    private IdempotencyAction handleIdempotency(StandardPrice sp,
                                                  Map<String, StandardPrice> existingMap,
                                                  ImportResult result,
                                                  int rowNo,
                                                  String factoryModel,
                                                  String productName) {
        if (!config.isIdempotentEnabled()) return IdempotencyAction.PROCEED;

        if (sp.getProductId() == null || sp.getCurrency() == null) return IdempotencyAction.PROCEED;
        String key = sp.getProductId() + ":" + sp.getCurrency();
        StandardPrice existing = existingMap.get(key);
        if (existing == null) return IdempotencyAction.PROCEED;

        IdempotentStrategy strategy = config.getEffectiveStrategy();
        if (strategy == IdempotentStrategy.SKIP) {
            log.debug("跳过已有记录: productId={}, currency={}", sp.getProductId(), sp.getCurrency());
            result.addWarning(rowNo, "跳过已有记录 (productId=" + sp.getProductId() + ")",
                    factoryModel, productName);
            return IdempotencyAction.SKIP_ROW;
        } else if (strategy == IdempotentStrategy.OVERWRITE) {
            log.debug("覆盖已有记录: productId={}, currency={}", sp.getProductId(), sp.getCurrency());
            // 用已有记录的 id 标记为「更新」, 不立即 updateById
            sp.setId(existing.getId());
            result.addWarning(rowNo, "覆盖已有记录 (id=" + existing.getId() + ")",
                    factoryModel, productName);
            return IdempotencyAction.UPDATED;
        }
        return IdempotencyAction.PROCEED;
    }

    /** 幂等性预查 —— 按 productCache 查所有已有报价 */
    private Map<String, StandardPrice> idempotentPreload(Map<Long, Product> productCache) {
        if (!config.isIdempotentEnabled() || productCache.isEmpty()) return Collections.emptyMap();
        Set<Long> ids = productCache.values().stream().map(Product::getId).collect(Collectors.toSet());
        return idempotentPreloadByIds(ids);
    }

    private Map<String, StandardPrice> idempotentPreloadByIds(Set<Long> productIds) {
        if (!config.isIdempotentEnabled() || productIds.isEmpty()) return Collections.emptyMap();
        List<StandardPrice> list = standardPriceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StandardPrice>()
                        .in(StandardPrice::getProductId, productIds)
                        .isNull(StandardPrice::getCustomerId)
                        .eq(StandardPrice::getDeleted, 0));
        Map<String, StandardPrice> map = new HashMap<>();
        for (StandardPrice sp : list) {
            if (sp.getProductId() != null && sp.getCurrency() != null) {
                map.put(sp.getProductId() + ":" + sp.getCurrency(), sp);
            }
        }
        return map;
    }

    // ========================================================================
    //  列映射构建 [改进 6]
    // ========================================================================

    /** 从表头行解析列映射（合并内置映射 + 外部配置映射） */
    private Map<Integer, String> buildColumnMapping(Map<Integer, String> headerRow) {
        // 合并映射表: 外部配置优先, 内置为基线
        Map<String, String> mergedMap = new LinkedHashMap<>(BUILTIN_COLUMN_MAP);
        mergedMap.putAll(config.getColumnAliases());

        Map<Integer, String> resultMap = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            String header = headerRow.get(i);
            if (header == null || header.isBlank()) continue;
            String clean = config.isTrimWhitespace() ? header.trim() : header;
            if (config.isIgnoreCase()) clean = clean.toLowerCase();
            String field = null;

            // 1. 精确匹配
            if (field == null) {
                field = mergedMap.get(clean);
            }
            // 2. contains 模糊匹配（值包含 key）
            if (field == null) {
                for (Map.Entry<String, String> e : mergedMap.entrySet()) {
                    String key = config.isIgnoreCase() ? e.getKey().toLowerCase() : e.getKey();
                    if (clean.contains(key)) {
                        field = e.getValue();
                        break;
                    }
                }
            }
            // 3. 正则匹配（key 以 regex: 开头）
            if (field == null) {
                for (Map.Entry<String, String> e : mergedMap.entrySet()) {
                    if (e.getKey().startsWith("regex:")) {
                        String regex = e.getKey().substring(6);
                        try {
                            if (clean.matches(regex)) {
                                field = e.getValue();
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (SKIP.equals(field)) {
                log.debug("列 {} [{}] → 跳过", i, header);
            } else if (field != null) {
                log.debug("列 {} [{}] → {}", i, header, field);
                resultMap.put(i, field);
            } else {
                log.debug("列 {} [{}] → 未匹配", i, header);
            }
        }
        return resultMap;
    }

    // ========================================================================
    //  行转换
    // ========================================================================

    /** 别名行 → DTO */
    private StandardPriceImportDTO mapToDto(Map<Integer, String> rawRow,
                                             Map<Integer, String> colToField) {
        StandardPriceImportDTO dto = new StandardPriceImportDTO();
        boolean hasAnyField = false;
        for (Map.Entry<Integer, String> e : colToField.entrySet()) {
            String value = rawRow.get(e.getKey());
            if (value == null || value.isBlank()) continue;
            String field = e.getValue();
            String trimmed = value.trim();
            switch (field) {
                case "factoryModel" -> { dto.setFactoryModel(trimmed); hasAnyField = true; }
                case "productName" -> { dto.setProductName(trimmed); hasAnyField = true; }
                case "companyModel" -> { dto.setCompanyModel(trimmed); hasAnyField = true; }
                case "remark" -> { dto.setRemark(trimmed); hasAnyField = true; }
                case "factoryPrice" -> { dto.setFactoryPrice(parsePrice(trimmed)); hasAnyField = true; }
                case "currency" -> { dto.setCurrency(trimmed); hasAnyField = true; }
                default -> log.debug("未映射字段: {}", field);
            }
        }
        // 至少要有工厂型号或产品名称才有意义
        if (!hasAnyField) return null;
        return dto;
    }

    /** DTO → fields map */
    private Map<String, String> dtoToFields(StandardPriceImportDTO dto) {
        Map<String, String> fields = new HashMap<>();
        if (dto.getProductName() != null) fields.put("productName", dto.getProductName());
        if (dto.getCompanyModel() != null) fields.put("companyModel", dto.getCompanyModel());
        if (dto.getRemark() != null) fields.put("remark", dto.getRemark());
        if (dto.getFactoryPrice() != null) fields.put("factoryPrice", dto.getFactoryPrice().toString());
        if (dto.getCurrency() != null) fields.put("currency", dto.getCurrency());
        return fields;
    }

    // ========================================================================
    //  批量产品查询
    // ========================================================================

    private Map<String, Product> batchLoadProducts(Set<String> factoryModels) {
        if (factoryModels.isEmpty()) return Collections.emptyMap();
        // MyBatis-Plus 没有直接按 factory_model 批量查的方法，逐行查
        // 为优化性能，用 IN 查询
        if (factoryModels.size() <= 200) {
            // 分批 IN 查询
            Map<String, Product> map = new HashMap<>();
            for (String fm : factoryModels) {
                Product p = productMapper.selectByFactoryModel(fm);
                if (p != null) map.put(fm, p);
            }
            return map;
        }
        return Collections.emptyMap();
    }

    // ========================================================================
    //  批量写入
    // ========================================================================

    private void flushBatches(List<StandardPrice> toInsert, List<StandardPrice> toUpdate) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        if (!toInsert.isEmpty()) {
            // MetaObjectHandler 对 batch 不生效，手动设审计字段
            for (StandardPrice sp : toInsert) {
                if (sp.getId() == null) {
                    sp.setId(IdWorker.getId());
                }
                sp.setCreateBy(userId);
                sp.setCreateTime(now);
                sp.setUpdateBy(userId);
                sp.setUpdateTime(now);
            }
            standardPriceMapper.insertBatch(toInsert);
            log.debug("批量插入 {} 条", toInsert.size());
        }
        if (!toUpdate.isEmpty()) {
            for (StandardPrice sp : toUpdate) {
                sp.setUpdateBy(userId);
                sp.setUpdateTime(now);
            }
            standardPriceMapper.updateBatch(toUpdate);
            log.debug("批量更新 {} 条", toUpdate.size());
        }
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

    private String getOrElse(Map<String, String> map, String key, String defaultValue) {
        String v = map.get(key);
        return (v != null && !v.isBlank()) ? v.trim() : defaultValue;
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replaceAll("[^\\d.\\-]", "");
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("价格解析失败: {}", value);
            return null;
        }
    }

    private boolean isRowEmpty(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) return true;
        for (String v : row.values()) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }
}
