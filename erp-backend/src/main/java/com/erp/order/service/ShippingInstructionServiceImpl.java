package com.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataAccessGuard;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.entity.ShippingInstructionItem;
import com.erp.order.mapper.ShippingInstructionItemMapper;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import com.erp.task.service.PersonalTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShippingInstructionServiceImpl extends ServiceImpl<ShippingInstructionMapper, ShippingInstruction>
        implements ShippingInstructionService {

    private static final SecureRandom SI_NO_RANDOM = new SecureRandom();
    private static final int SI_NO_RANDOM_BOUND = 100000;

    private final CustomerMapper customerMapper;
    private final SalesPiMapper salesPiMapper;
    private final SalesPiItemMapper salesPiItemMapper;
    private final ShippingInstructionItemMapper itemMapper;
    private final SysUserMapper sysUserMapper;
    private final DataAccessGuard accessGuard;
    private final PersonalTaskService personalTaskService;

    public ShippingInstructionServiceImpl(CustomerMapper customerMapper,
                                          SalesPiMapper salesPiMapper,
                                          SalesPiItemMapper salesPiItemMapper,
                                          ShippingInstructionItemMapper itemMapper,
                                          SysUserMapper sysUserMapper,
                                          DataAccessGuard accessGuard,
                                          PersonalTaskService personalTaskService) {
        this.customerMapper = customerMapper;
        this.salesPiMapper = salesPiMapper;
        this.salesPiItemMapper = salesPiItemMapper;
        this.itemMapper = itemMapper;
        this.sysUserMapper = sysUserMapper;
        this.accessGuard = accessGuard;
        this.personalTaskService = personalTaskService;
    }

    @Override
    public IPage<ShippingInstruction> pageQuery(PageQuery query) {
        IPage<ShippingInstruction> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query.getKeyword()));
        hydrateCustomerCodes(page.getRecords());
        return page;
    }

    @Override
    public List<ShippingInstruction> listByKeyword(String keyword) {
        List<ShippingInstruction> list = this.list(buildWrapper(keyword));
        hydrateCustomerCodes(list);
        return list;
    }

    @Override
    public ShippingInstruction getDetail(Long id) {
        ShippingInstruction entity = getById(id);
        if (entity != null) {
            assertVisible(entity);
            hydrateCustomerCodes(List.of(entity));
            entity.setItems(loadDetailItems(entity));
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ShippingInstruction entity) {
        List<ShippingInstructionItem> submittedItems = normalizeItems(entity.getItems());
        List<ShippingInstructionItem> submittedLegacyItem = legacyItems(entity);
        entity.setCreateBy(accessGuard.currentUserId());
        prepare(entity);
        List<ShippingInstructionItem> items = resolveItemsForCreate(
                entity, submittedItems, submittedLegacyItem);
        entity.setItems(items);
        syncLegacySummary(entity, items);
        boolean saved = super.save(entity);
        if (saved) {
            saveItems(entity.getId(), items);
            syncCalendarTask(entity);
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ShippingInstruction entity) {
        boolean replaceItems = entity != null && entity.getItems() != null;
        if (entity != null) {
            ShippingInstruction existing = super.getById(entity.getId());
            assertVisible(existing);
            if (existing != null) {
                entity.setCreateBy(existing.getCreateBy());
            }
        }
        prepare(entity);
        List<ShippingInstructionItem> items = null;
        if (replaceItems) {
            items = resolveSubmittedItems(entity);
            entity.setItems(items);
            syncLegacySummary(entity, items);
        }
        boolean updated = super.updateById(entity);
        if (updated) {
            if (replaceItems) {
                // MyBatis-Plus 默认不会更新 null；显式同步可确保清空数量/箱数后列表摘要不残留。
                this.update(new LambdaUpdateWrapper<ShippingInstruction>()
                        .eq(ShippingInstruction::getId, entity.getId())
                        .set(ShippingInstruction::getPiId, entity.getPiId())
                        .set(ShippingInstruction::getProductModel, entity.getProductModel())
                        .set(ShippingInstruction::getFactoryModel, entity.getFactoryModel())
                        .set(ShippingInstruction::getQuantity, entity.getQuantity())
                        .set(ShippingInstruction::getCartonCount, entity.getCartonCount()));
                itemMapper.delete(new LambdaQueryWrapper<ShippingInstructionItem>()
                        .eq(ShippingInstructionItem::getSiId, entity.getId()));
                saveItems(entity.getId(), items);
            }
            syncCalendarTask(super.getById(entity.getId()));
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        assertVisible(super.getById(id));
        boolean removed = super.removeById(id);
        if (removed && id instanceof Long longId) {
            itemMapper.delete(new LambdaQueryWrapper<ShippingInstructionItem>()
                    .eq(ShippingInstructionItem::getSiId, longId));
            personalTaskService.removeSystemTask("SHIPPING_INSTRUCTION", longId);
        }
        return removed;
    }

    private void syncCalendarTask(ShippingInstruction entity) {
        if (entity == null || entity.getId() == null) {
            return;
        }
        Long ownerId = resolveOwnerId(entity);
        String title = "出货指示: " + firstText(entity.getSiNo(), entity.getOrderNo(), "未命名单据");
        String content = "出货日期 " + entity.getShipDate()
                + "\n订单编号: " + firstText(entity.getOrderNo(), "-")
                + "\n客户ID: " + firstText(entity.getCustomerCode(), "-")
                + "\n产品型号: " + firstText(entity.getProductModel(), "-");
        personalTaskService.upsertSystemTask(ownerId, title, content, entity.getShipDate(),
                "HIGH", "SHIPPING_INSTRUCTION", "SHIPPING_INSTRUCTION", entity.getId(), entity.getSiNo());
    }

    private Long resolveOwnerId(ShippingInstruction entity) {
        if (entity.getPiId() != null) {
            SalesPi pi = salesPiMapper.selectById(entity.getPiId());
            if (pi != null && pi.getOwnerId() != null) {
                return pi.getOwnerId();
            }
        }
        return entity.getCreateBy();
    }

    private LambdaQueryWrapper<ShippingInstruction> buildWrapper(String keyword) {
        LambdaQueryWrapper<ShippingInstruction> wrapper = new LambdaQueryWrapper<ShippingInstruction>()
                .orderByDesc(ShippingInstruction::getCreateTime);
        Long currentUserId = accessGuard.restrictOwnerId();
        if (currentUserId != null) {
            List<Long> piIds = accessGuard.visiblePiIdsForCurrentUser();
            wrapper.and(w -> {
                if (!piIds.isEmpty()) {
                    w.in(ShippingInstruction::getPiId, piIds).or();
                }
                w.isNull(ShippingInstruction::getPiId)
                        .eq(ShippingInstruction::getCreateBy, currentUserId);
            });
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                            .select(Customer::getId)
                            .like(Customer::getCustCode, kw))
                    .stream().map(Customer::getId).toList();
            wrapper.and(w -> {
                w.like(ShippingInstruction::getSiNo, kw)
                        .or().like(ShippingInstruction::getOrderNo, kw)
                        .or().like(ShippingInstruction::getOrderCountry, kw)
                        .or().like(ShippingInstruction::getProductModel, kw)
                        .or().like(ShippingInstruction::getFactoryModel, kw)
                        .or().like(ShippingInstruction::getOwnerName, kw);
                if (!customerIds.isEmpty()) {
                    w.or().in(ShippingInstruction::getCustomerId, customerIds);
                }
                w.or().apply("EXISTS (SELECT 1 FROM shipping_instruction_item sii "
                                + "WHERE sii.si_id = shipping_instruction.id AND sii.deleted = 0 "
                                + "AND (sii.product_model LIKE CONCAT('%', {0}, '%') "
                                + "OR sii.factory_model LIKE CONCAT('%', {0}, '%')))",
                        kw);
            });
        }
        return wrapper;
    }

    private void prepare(ShippingInstruction entity) {
        if (entity == null) {
            return;
        }
        fillCustomerId(entity);
        if (entity.getPiId() != null) {
            accessGuard.assertPiVisible(entity.getPiId());
        } else {
            accessGuard.assertCustomerVisible(entity.getCustomerId());
        }
        fillSiNo(entity);
        fillFromPi(entity, entity.getId() == null);
        if (entity.getCartonCount() == null && entity.getQuantity() != null) {
            entity.setCartonCount(entity.getQuantity());
        }
    }

    private void fillSiNo(ShippingInstruction entity) {
        if (StringUtils.hasText(entity.getSiNo())) {
            return;
        }
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String siNo;
        do {
            siNo = "SI" + datePart + "-" + String.format("%05d", SI_NO_RANDOM.nextInt(SI_NO_RANDOM_BOUND));
        } while (this.count(new LambdaQueryWrapper<ShippingInstruction>().eq(ShippingInstruction::getSiNo, siNo)) > 0);
        entity.setSiNo(siNo);
    }

    private void fillFromPi(ShippingInstruction entity, boolean applyDefaults) {
        if (entity.getPiId() == null) {
            return;
        }
        SalesPi pi = salesPiMapper.selectById(entity.getPiId());
        if (pi == null) {
            throw new BusinessException("关联PI不存在");
        }
        if (!java.util.Set.of("ORDERED", "SHIPPED", "COMPLETED", "CLOSED").contains(pi.getStatus())) {
            throw new BusinessException("PI 收到定金并进入已下单状态后才能创建出货指示");
        }
        if (!applyDefaults) {
            return;
        }
        Customer customer = pi.getCustomerId() != null ? customerMapper.selectById(pi.getCustomerId()) : null;
        List<SalesPiItem> items = salesPiItemMapper.selectList(new LambdaQueryWrapper<SalesPiItem>()
                .eq(SalesPiItem::getPiId, pi.getId())
                .orderByAsc(SalesPiItem::getId));

        // PI provides defaults only. Shipping and consignee details are a document snapshot
        // and may legitimately differ from the customer's current master data.
        if (entity.getCustomerId() == null && !StringUtils.hasText(entity.getCustomerCode())) {
            entity.setCustomerId(pi.getCustomerId());
            if (customer != null) {
                entity.setCustomerCode(customer.getCustCode());
            }
        }
        setIfBlank(entity::getOrderNo, entity::setOrderNo, pi.getPiNo());
        setIfBlank(entity::getOrderCountry, entity::setOrderCountry, customer != null ? customer.getCountry() : null);
        setIfBlank(entity::getContactName, entity::setContactName, customer != null ? firstText(customer.getContactName(), customer.getContact()) : null);
        setIfBlank(entity::getPhone, entity::setPhone, customer != null ? customer.getPhone() : null);
        setIfBlank(entity::getDeliveryAddress, entity::setDeliveryAddress, customer != null ? customer.getAddress() : null);
        setIfBlank(entity::getOrderRequirement, entity::setOrderRequirement, firstText(joinItemRemarks(items), pi.getShippingMarks(), pi.getRemark()));
        if (entity.getOrderDate() == null) {
            entity.setOrderDate(pi.getSignDate());
        }
        if (entity.getShipDate() == null) {
            entity.setShipDate(pi.getDeliveryDate());
        }
        if (!StringUtils.hasText(entity.getOwnerName()) && pi.getOwnerId() != null) {
            SysUser owner = sysUserMapper.selectById(pi.getOwnerId());
            if (owner != null) {
                entity.setOwnerName(firstText(owner.getRealName(), owner.getNickname(), owner.getUsername()));
            }
        }
        setIfBlank(entity::getProductModel, entity::setProductModel, joinDistinct(items.stream().map(SalesPiItem::getProductModel).toList()));
        setIfBlank(entity::getFactoryModel, entity::setFactoryModel, joinDistinct(items.stream().map(SalesPiItem::getFactoryModel).toList()));
        if (entity.getQuantity() == null) {
            BigDecimal totalQty = items.stream()
                    .map(SalesPiItem::getQty)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalQty.signum() > 0) {
                entity.setQuantity(totalQty);
            }
        }
    }

    /**
     * 新建时优先固化前端提交的明细；若尚未填写，则从关联 PI 生成产品快照。
     */
    private List<ShippingInstructionItem> resolveItemsForCreate(
            ShippingInstruction entity,
            List<ShippingInstructionItem> submitted,
            List<ShippingInstructionItem> submittedLegacyItem) {
        if (hasItemContent(submitted)) {
            return submitted;
        }
        // 旧客户端仍可能只提交主表单行字段，不能被关联 PI 的实时明细覆盖。
        if (hasItemContent(submittedLegacyItem)) {
            return submittedLegacyItem;
        }
        List<ShippingInstructionItem> piItems = loadPiItems(entity);
        if (!piItems.isEmpty()) {
            return piItems;
        }
        if (!submitted.isEmpty()) {
            return submitted;
        }
        return Collections.emptyList();
    }

    /**
     * PUT 中 items 非空表示新界面明确提交了产品区，空白时也保留一行，避免回退到实时 PI。
     */
    private List<ShippingInstructionItem> resolveSubmittedItems(ShippingInstruction entity) {
        List<ShippingInstructionItem> items = normalizeItems(entity.getItems());
        if (!items.isEmpty()) {
            return items;
        }
        return List.of(new ShippingInstructionItem());
    }

    /** 已保存明细优先；历史单据没有明细时兼容关联 PI 和旧主表字段。 */
    private List<ShippingInstructionItem> loadDetailItems(ShippingInstruction entity) {
        List<ShippingInstructionItem> stored = itemMapper.selectList(
                new LambdaQueryWrapper<ShippingInstructionItem>()
                        .eq(ShippingInstructionItem::getSiId, entity.getId())
                        .orderByAsc(ShippingInstructionItem::getSortOrder)
                        .orderByAsc(ShippingInstructionItem::getId));
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        List<ShippingInstructionItem> legacy = legacyItems(entity);
        List<ShippingInstructionItem> piItems = loadPiItems(entity);
        if (piItems.isEmpty()) {
            return legacy;
        }
        if (legacy.isEmpty()) {
            return piItems;
        }
        // 旧主表摘要仍与当前 PI 一致时，借助 PI 恢复成多行；PI 已变化则保留历史快照。
        return legacySummaryMatchesPi(entity, piItems) ? piItems : legacy;
    }

    private boolean legacySummaryMatchesPi(ShippingInstruction entity,
                                           List<ShippingInstructionItem> piItems) {
        String piProductModels = limitSummary(joinDistinct(piItems.stream()
                .map(ShippingInstructionItem::getProductModel).toList()));
        String piFactoryModels = limitSummary(joinDistinct(piItems.stream()
                .map(ShippingInstructionItem::getFactoryModel).toList()));
        BigDecimal piQuantity = sumNullable(piItems.stream()
                .map(ShippingInstructionItem::getQuantity).toList());
        return normalizeSummaryText(entity.getProductModel()).equals(normalizeSummaryText(piProductModels))
                && normalizeSummaryText(entity.getFactoryModel()).equals(normalizeSummaryText(piFactoryModels))
                && sameNumber(entity.getQuantity(), piQuantity);
    }

    private String normalizeSummaryText(String value) {
        return StringUtils.hasText(value) ? value.trim().replace("\r\n", "\n") : "";
    }

    private boolean sameNumber(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private List<ShippingInstructionItem> loadPiItems(ShippingInstruction entity) {
        if (entity.getPiId() == null) {
            return Collections.emptyList();
        }
        List<SalesPiItem> source = salesPiItemMapper.selectList(new LambdaQueryWrapper<SalesPiItem>()
                .eq(SalesPiItem::getPiId, entity.getPiId())
                .orderByAsc(SalesPiItem::getId));
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<BigDecimal> cartons = allocateCartonCounts(source, entity.getCartonCount());
        List<ShippingInstructionItem> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            SalesPiItem sourceItem = source.get(index);
            ShippingInstructionItem item = new ShippingInstructionItem();
            item.setSiId(entity.getId());
            item.setProductModel(trimToNull(sourceItem.getProductModel()));
            item.setFactoryModel(trimToNull(sourceItem.getFactoryModel()));
            item.setQuantity(sourceItem.getQty());
            item.setCartonCount(cartons.get(index));
            item.setSortOrder(index);
            result.add(item);
        }
        return result;
    }

    private List<BigDecimal> allocateCartonCounts(List<SalesPiItem> items, BigDecimal totalCartons) {
        if (totalCartons == null) {
            return items.stream().map(SalesPiItem::getQty).toList();
        }
        if (items.size() == 1) {
            return List.of(totalCartons);
        }
        BigDecimal totalQuantity = items.stream()
                .map(SalesPiItem::getQty)
                .filter(value -> value != null && value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.signum() <= 0) {
            List<BigDecimal> result = new ArrayList<>(Collections.nCopies(items.size(), BigDecimal.ZERO));
            result.set(0, totalCartons);
            return result;
        }
        int scale = Math.max(0, totalCartons.stripTrailingZeros().scale());
        BigDecimal allocated = BigDecimal.ZERO;
        List<BigDecimal> result = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            if (index == items.size() - 1) {
                result.add(totalCartons.subtract(allocated));
                continue;
            }
            BigDecimal quantity = items.get(index).getQty();
            BigDecimal cartons = quantity == null || quantity.signum() <= 0
                    ? BigDecimal.ZERO
                    : totalCartons.multiply(quantity).divide(totalQuantity, scale, java.math.RoundingMode.DOWN);
            result.add(cartons);
            allocated = allocated.add(cartons);
        }
        return result;
    }

    private List<ShippingInstructionItem> legacyItems(ShippingInstruction entity) {
        if (!StringUtils.hasText(entity.getProductModel())
                && !StringUtils.hasText(entity.getFactoryModel())
                && entity.getQuantity() == null
                && entity.getCartonCount() == null) {
            return Collections.emptyList();
        }
        ShippingInstructionItem item = new ShippingInstructionItem();
        item.setSiId(entity.getId());
        item.setProductModel(trimToNull(entity.getProductModel()));
        item.setFactoryModel(trimToNull(entity.getFactoryModel()));
        item.setQuantity(entity.getQuantity());
        item.setCartonCount(entity.getCartonCount() != null ? entity.getCartonCount() : entity.getQuantity());
        item.setSortOrder(0);
        return List.of(item);
    }

    private List<ShippingInstructionItem> normalizeItems(List<ShippingInstructionItem> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (source.size() > 200) {
            throw new BusinessException("一张出货指示最多添加 200 条产品明细");
        }
        List<ShippingInstructionItem> result = new ArrayList<>(source.size());
        ShippingInstructionItem firstBlank = null;
        for (int sourceIndex = 0; sourceIndex < source.size(); sourceIndex++) {
            ShippingInstructionItem sourceItem = source.get(sourceIndex);
            if (sourceItem == null) {
                continue;
            }
            ShippingInstructionItem item = new ShippingInstructionItem();
            item.setProductModel(trimToNull(sourceItem.getProductModel()));
            item.setFactoryModel(trimToNull(sourceItem.getFactoryModel()));
            item.setQuantity(sourceItem.getQuantity());
            item.setCartonCount(sourceItem.getCartonCount() != null
                    ? sourceItem.getCartonCount() : sourceItem.getQuantity());
            if (hasItemContent(item)) {
                validateSubmittedItem(item, sourceIndex + 1);
                item.setSortOrder(result.size());
                result.add(item);
            } else if (firstBlank == null) {
                firstBlank = item;
            }
        }
        if (result.isEmpty() && firstBlank != null) {
            firstBlank.setSortOrder(0);
            result.add(firstBlank);
        }
        return result;
    }

    private void validateSubmittedItem(ShippingInstructionItem item, int rowNo) {
        validateStorageItem(item, rowNo);
    }

    private void validateStorageItem(ShippingInstructionItem item, int rowNo) {
        if ((item.getProductModel() != null && item.getProductModel().length() > 512)
                || (item.getFactoryModel() != null && item.getFactoryModel().length() > 512)) {
            throw new BusinessException("第 " + rowNo + " 条产品明细的型号最多 512 个字符");
        }
        if (item.getQuantity() != null && item.getQuantity().signum() < 0) {
            throw new BusinessException("第 " + rowNo + " 条产品明细的数量不能小于 0");
        }
        if (item.getQuantity() != null && !fitsDecimal18Scale4(item.getQuantity())) {
            throw new BusinessException("第 " + rowNo + " 条产品明细的数量最多 14 位整数和 4 位小数");
        }
        if (item.getCartonCount() != null && item.getCartonCount().signum() < 0) {
            throw new BusinessException("第 " + rowNo + " 条产品明细的箱数不能小于 0");
        }
        if (item.getCartonCount() != null && !fitsDecimal18Scale4(item.getCartonCount())) {
            throw new BusinessException("第 " + rowNo + " 条产品明细的箱数最多 14 位整数和 4 位小数");
        }
    }

    private boolean fitsDecimal18Scale4(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = Math.max(0, normalized.scale());
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        return scale <= 4 && integerDigits <= 14;
    }

    private boolean hasItemContent(List<ShippingInstructionItem> items) {
        return items != null && items.stream().anyMatch(this::hasItemContent);
    }

    private boolean hasItemContent(ShippingInstructionItem item) {
        return item != null && (StringUtils.hasText(item.getProductModel())
                || StringUtils.hasText(item.getFactoryModel())
                || item.getQuantity() != null
                || item.getCartonCount() != null);
    }

    private void syncLegacySummary(ShippingInstruction entity, List<ShippingInstructionItem> items) {
        List<ShippingInstructionItem> safeItems = items == null ? Collections.emptyList() : items;
        entity.setProductModel(limitSummary(joinDistinct(safeItems.stream()
                .map(ShippingInstructionItem::getProductModel).toList())));
        entity.setFactoryModel(limitSummary(joinDistinct(safeItems.stream()
                .map(ShippingInstructionItem::getFactoryModel).toList())));
        BigDecimal totalQuantity = sumNullable(safeItems.stream()
                .map(ShippingInstructionItem::getQuantity).toList());
        BigDecimal totalCartons = sumNullable(safeItems.stream()
                .map(ShippingInstructionItem::getCartonCount).toList());
        if (totalQuantity != null && !fitsDecimal18Scale4(totalQuantity)) {
            throw new BusinessException("产品数量合计最多 14 位整数和 4 位小数");
        }
        if (totalCartons != null && !fitsDecimal18Scale4(totalCartons)) {
            throw new BusinessException("产品箱数合计最多 14 位整数和 4 位小数");
        }
        entity.setQuantity(totalQuantity);
        entity.setCartonCount(totalCartons);
    }

    private BigDecimal sumNullable(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void saveItems(Long siId, List<ShippingInstructionItem> items) {
        if (siId == null || items == null) {
            return;
        }
        if (items.size() > 200) {
            throw new BusinessException("一张出货指示最多添加 200 条产品明细");
        }
        for (int index = 0; index < items.size(); index++) {
            ShippingInstructionItem item = items.get(index);
            validateStorageItem(item, index + 1);
            item.setId(null);
            item.setSiId(siId);
            item.setSortOrder(index);
            itemMapper.insert(item);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limitSummary(String value) {
        if (value == null || value.length() <= 512) {
            return value;
        }
        return value.substring(0, 509) + "...";
    }

    private void fillCustomerId(ShippingInstruction entity) {
        if (!StringUtils.hasText(entity.getCustomerCode())) {
            return;
        }
        Long id = customerMapper.selectIdByCode(entity.getCustomerCode().trim());
        if (id == null) {
            throw new BusinessException("客户ID不存在: " + entity.getCustomerCode());
        }
        entity.setCustomerId(id);
    }

    private void hydrateCustomerCodes(List<ShippingInstruction> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream()
                .map(ShippingInstruction::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Customer> customers = new HashMap<>(customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a)));
        for (ShippingInstruction record : records) {
            Customer customer = customers.get(record.getCustomerId());
            if (customer != null) {
                record.setCustomerCode(customer.getCustCode());
            }
        }
    }

    private String joinItemRemarks(List<SalesPiItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return joinDistinct(items.stream().map(SalesPiItem::getRemark).toList());
    }

    private String joinDistinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    private void setIfBlank(java.util.function.Supplier<String> getter,
                            java.util.function.Consumer<String> setter,
                            String value) {
        if (!StringUtils.hasText(getter.get()) && StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void assertVisible(ShippingInstruction entity) {
        if (entity == null) {
            return;
        }
        accessGuard.assertRecordVisible(entity.getPiId(), null, entity.getCreateBy(), "出货指示");
    }
}
