package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.inventory.dto.StockRow;
import com.erp.inventory.entity.StockMovement;
import com.erp.inventory.mapper.StockMovementMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存服务 - 出入库流水 + 结存计算(IN-OUT) + 安全库存预警。
 */
@Slf4j
@Service
public class StockService {

    private final ProductMapper productMapper;
    private final StockMovementMapper movementMapper;

    public StockService(ProductMapper productMapper, StockMovementMapper movementMapper) {
        this.productMapper = productMapper;
        this.movementMapper = movementMapper;
    }

    /** 结存分页；产品主数据和只有库存流水的手工产品代码都会显示。 */
    public IPage<StockRow> pageStock(PageQuery query, Boolean lowOnly) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getCompanyModel, Product::getProdCode));
        StockSnapshot snapshot = stockSnapshot(products);
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim().toLowerCase() : null;

        List<StockRow> rows = new ArrayList<>();
        for (Product product : products) {
            String displayCode = firstText(product.getCompanyModel(), product.getProdCode());
            String displayName = firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn());
            if (!matchesKeyword(keyword, displayCode, product.getProdCode(), displayName,
                    product.getProdNameCn(), product.getProdNameEn())) {
                continue;
            }
            BigDecimal stock = snapshot.byProductId().getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal safety = product.getSafetyStock();
            boolean low = safety != null && safety.signum() > 0 && stock.compareTo(safety) < 0;
            if (Boolean.TRUE.equals(lowOnly) && !low) {
                continue;
            }
            StockRow row = new StockRow();
            row.setProductId(product.getId());
            row.setProdCode(displayCode);
            row.setProdName(displayName);
            row.setUnit(product.getUnit());
            row.setCurrentStock(stock);
            row.setSafetyStock(safety);
            row.setLowStock(low);
            rows.add(row);
        }

        if (!Boolean.TRUE.equals(lowOnly)) {
            for (StockAccumulator orphan : snapshot.orphanByCode().values()) {
                if (!matchesKeyword(keyword, orphan.code, orphan.productName)) {
                    continue;
                }
                StockRow row = new StockRow();
                row.setProdCode(orphan.code);
                row.setProdName(firstText(orphan.productName, orphan.code));
                row.setCurrentStock(orphan.qty);
                row.setLowStock(false);
                rows.add(row);
            }
        }

        rows.sort(Comparator.comparing(row -> firstText(row.getProdCode(), ""), String.CASE_INSENSITIVE_ORDER));
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.max(1, query.getPageSize());
        int from = (int) Math.min((pageNum - 1) * pageSize, rows.size());
        int to = (int) Math.min(from + pageSize, rows.size());
        Page<StockRow> page = new Page<>(pageNum, pageSize, rows.size());
        page.setRecords(rows.subList(from, to));
        return page;
    }

    /** 某产品的出入库流水；公司型号和内部产品代码视为同一产品。 */
    public IPage<StockMovement> movements(String prodCode, PageQuery query) {
        LambdaQueryWrapper<StockMovement> wrapper = new LambdaQueryWrapper<StockMovement>()
                .orderByDesc(StockMovement::getMoveDate, StockMovement::getId);
        Product product = findProduct(prodCode);
        if (product != null) {
            List<String> aliases = productAliases(product, prodCode);
            wrapper.and(w -> w.eq(StockMovement::getProductId, product.getId())
                    .or(alias -> alias.isNull(StockMovement::getProductId)
                            .in(StockMovement::getProdCode, aliases)));
        } else if (StringUtils.hasText(prodCode)) {
            wrapper.eq(StockMovement::getProdCode, prodCode.trim());
        }
        return movementMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    /** 低于安全库存的产品数。 */
    public long lowStockCount() {
        long count = 0;
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .isNotNull(Product::getSafetyStock).gt(Product::getSafetyStock, BigDecimal.ZERO));
        Map<Long, BigDecimal> stockMap = stockSnapshot(products).byProductId();
        for (Product product : products) {
            if (stockMap.getOrDefault(product.getId(), BigDecimal.ZERO).compareTo(product.getSafetyStock()) < 0) {
                count++;
            }
        }
        return count;
    }

    /** 当前结存；按产品ID汇总并兼容产品代码别名。 */
    public BigDecimal currentStock(String prodCode) {
        if (!StringUtils.hasText(prodCode)) {
            return BigDecimal.ZERO;
        }
        Product product = findProduct(prodCode);
        QueryWrapper<StockMovement> wrapper = new QueryWrapper<StockMovement>()
                .select("direction", "SUM(qty) AS q")
                .groupBy("direction");
        if (product != null) {
            List<String> aliases = productAliases(product, prodCode);
            wrapper.and(w -> w.eq("product_id", product.getId())
                    .or(alias -> alias.isNull("product_id").in("prod_code", aliases)));
        } else {
            wrapper.eq("prod_code", prodCode.trim());
        }
        return sumMovements(movementMapper.selectMaps(wrapper));
    }

    /** 从库存查询页设置产品安全库存；0 表示关闭该产品的低库存预警。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateSafetyStock(Long productId, BigDecimal safetyStock) {
        if (productId == null) {
            throw new BusinessException("缺少产品 ID");
        }
        if (safetyStock == null || safetyStock.signum() < 0) {
            throw new BusinessException("安全库存不能小于 0");
        }
        Product existing = productMapper.selectById(productId);
        if (existing == null) {
            throw new BusinessException("产品不存在，无法设置安全库存");
        }
        Product update = new Product();
        update.setId(productId);
        update.setSafetyStock(safetyStock);
        productMapper.updateById(update);
    }

    /** 记一笔出入库流水。 */
    @Transactional(rollbackFor = Exception.class)
    public Long recordMovement(String prodCode, String direction, BigDecimal qty, String bizType,
                               String relatedDocNo, LocalDate moveDate, String remark) {
        return recordMovement(prodCode, null, null, direction, qty, bizType, relatedDocNo, moveDate, remark);
    }

    /** 记录可关联产品主数据或手工产品名称的出入库流水。 */
    @Transactional(rollbackFor = Exception.class)
    public Long recordMovement(String prodCode, Long productId, String productName, String direction, BigDecimal qty,
                               String bizType, String relatedDocNo, LocalDate moveDate, String remark) {
        if (!StringUtils.hasText(prodCode)) {
            throw new BusinessException("产品代码不能为空");
        }
        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            throw new BusinessException("方向必须是 IN 或 OUT");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("数量必须大于 0");
        }
        StockMovement movement = new StockMovement();
        movement.setProdCode(prodCode.trim());
        Product product = productId != null ? productMapper.selectById(productId) : findProduct(prodCode);
        movement.setProductId(product != null ? product.getId() : productId);
        movement.setProductName(StringUtils.hasText(productName) ? productName.trim()
                : (product != null ? firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn()) : null));
        movement.setDirection(direction);
        movement.setQty(qty);
        movement.setBizType(StringUtils.hasText(bizType) ? bizType : "MANUAL");
        movement.setRelatedDocNo(relatedDocNo);
        movement.setMoveDate(moveDate != null ? moveDate : LocalDate.now());
        movement.setRemark(remark);
        movementMapper.insert(movement);
        log.info("[STOCK] {} {} {} ({})", direction, prodCode, qty, movement.getBizType());
        return movement.getId();
    }

    /**
     * 库存以 product_id 为主键汇总，产品代码和公司型号仅作为兼容别名。
     * 未匹配产品主数据的流水单独保留，确保手工代码的 PO 入库也能显示。
     */
    private StockSnapshot stockSnapshot(List<Product> products) {
        Map<Long, BigDecimal> byProductId = new HashMap<>();
        Map<String, Long> productIdByAlias = new HashMap<>();
        Map<Long, Product> productById = new HashMap<>();
        for (Product product : products) {
            if (product.getId() == null) {
                continue;
            }
            productById.put(product.getId(), product);
            for (String alias : productAliases(product, null)) {
                productIdByAlias.putIfAbsent(normalizeCode(alias), product.getId());
            }
        }

        Map<String, StockAccumulator> orphanByCode = new LinkedHashMap<>();
        List<Map<String, Object>> sums = movementMapper.selectMaps(new QueryWrapper<StockMovement>()
                .select("product_id", "prod_code", "MAX(product_name) AS product_name", "direction", "SUM(qty) AS q")
                .groupBy("product_id", "prod_code", "direction"));
        for (Map<String, Object> row : sums) {
            BigDecimal signed = signedQuantity(row);
            if (signed == null) {
                continue;
            }
            Long productId = toLong(row.get("product_id"));
            String code = valueText(row.get("prod_code"));
            if (productId == null && StringUtils.hasText(code)) {
                productId = productIdByAlias.get(normalizeCode(code));
            }
            if (productId != null && productById.containsKey(productId)) {
                byProductId.merge(productId, signed, BigDecimal::add);
                continue;
            }
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String key = normalizeCode(code);
            StockAccumulator accumulator = orphanByCode.computeIfAbsent(key,
                    ignored -> new StockAccumulator(code, valueText(row.get("product_name"))));
            accumulator.qty = accumulator.qty.add(signed);
            if (!StringUtils.hasText(accumulator.productName)) {
                accumulator.productName = valueText(row.get("product_name"));
            }
        }
        return new StockSnapshot(byProductId, orphanByCode);
    }

    private Product findProduct(String prodCode) {
        if (!StringUtils.hasText(prodCode)) {
            return null;
        }
        String code = prodCode.trim();
        return productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .and(w -> w.eq(Product::getProdCode, code).or().eq(Product::getCompanyModel, code))
                .last("LIMIT 1"));
    }

    private List<String> productAliases(Product product, String requestedCode) {
        List<String> aliases = new ArrayList<>();
        addAlias(aliases, product == null ? null : product.getProdCode());
        addAlias(aliases, product == null ? null : product.getCompanyModel());
        addAlias(aliases, requestedCode);
        return aliases;
    }

    private void addAlias(List<String> aliases, String value) {
        if (StringUtils.hasText(value) && aliases.stream().noneMatch(alias -> alias.equalsIgnoreCase(value.trim()))) {
            aliases.add(value.trim());
        }
    }

    private BigDecimal sumMovements(List<Map<String, Object>> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal signed = signedQuantity(row);
            if (signed != null) {
                total = total.add(signed);
            }
        }
        return total;
    }

    private BigDecimal signedQuantity(Map<String, Object> row) {
        Object qty = row.get("q");
        if (qty == null) {
            return null;
        }
        BigDecimal value = new BigDecimal(qty.toString());
        return "OUT".equals(row.get("direction")) ? value.negate() : value;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String valueText(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean matchesKeyword(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        for (String value : values) {
            if (StringUtils.hasText(value) && value.toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record StockSnapshot(Map<Long, BigDecimal> byProductId,
                                 Map<String, StockAccumulator> orphanByCode) {
    }

    private static final class StockAccumulator {
        private final String code;
        private String productName;
        private BigDecimal qty = BigDecimal.ZERO;

        private StockAccumulator(String code, String productName) {
            this.code = code;
            this.productName = productName;
        }
    }
}
