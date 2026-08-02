package com.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataAccessGuard;
import com.erp.order.dto.PurchaseOrderDTO;
import com.erp.order.dto.PoReceiptDTO;
import com.erp.order.dto.PoReceiptItemDTO;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.entity.PurchaseOrderItem;
import com.erp.order.mapper.PurchaseOrderItemMapper;
import com.erp.order.mapper.PurchaseOrderMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.inventory.service.StockService;
import com.erp.finance.service.PayableService;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import com.erp.order.dto.PurchaserOption;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {

    private static final DateTimeFormatter PO_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurchaseOrderItemMapper itemMapper;
    private final StockService stockService;
    private final DataAccessGuard accessGuard;
    private final SalesPiMapper salesPiMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper userMapper;
    private final PayableService payableService;

    public PurchaseOrderServiceImpl(PurchaseOrderItemMapper itemMapper, StockService stockService,
                                    DataAccessGuard accessGuard, SalesPiMapper salesPiMapper, ProductMapper productMapper,
                                    SysUserMapper userMapper, PayableService payableService) {
        this.itemMapper = itemMapper;
        this.stockService = stockService;
        this.accessGuard = accessGuard;
        this.salesPiMapper = salesPiMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.payableService = payableService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        PurchaseOrder po = this.getById(id);
        if (po == null) {
            throw new BusinessException("PO 不存在");
        }
        if ("CONFIRMED".equals(po.getStatus()) || "IN_PROGRESS".equals(po.getStatus())
                || "COMPLETED".equals(po.getStatus()) || "RECEIVED".equals(po.getStatus())) {
            return;
        }
        if (!"DRAFT".equals(po.getStatus()) && StringUtils.hasText(po.getStatus())) {
            throw new BusinessException("当前 PO 状态不能确认");
        }
        validateItemsForConfirmation(listItems(id));
        po.setStatus("CONFIRMED");
        this.updateById(po);
    }

    private void validateItemsForConfirmation(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("确认 PO 前请至少添加一条采购明细");
        }
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            int rowNo = i + 1;
            if (!StringUtils.hasText(item.getProdCode())) {
                throw new BusinessException("第 " + rowNo + " 条采购明细缺少产品代码");
            }
            if (item.getQty() == null || item.getQty().signum() <= 0) {
                throw new BusinessException("第 " + rowNo + " 条采购明细的采购数量必须大于 0");
            }
            BigDecimal receivedQty = zero(item.getReceivedQty());
            if (receivedQty.signum() < 0 || receivedQty.compareTo(item.getQty()) > 0) {
                throw new BusinessException("第 " + rowNo + " 条采购明细的已入库数量不合法");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id, PoReceiptDTO receipt) {
        PurchaseOrder po = this.getById(id);
        if (po == null) {
            throw new BusinessException("PO 不存在");
        }
        if ("DRAFT".equals(po.getStatus()) || !StringUtils.hasText(po.getStatus())) {
            throw new BusinessException("请先确认 PO 后再登记到货入库");
        }
        if ("COMPLETED".equals(po.getStatus()) || "RECEIVED".equals(po.getStatus())) {
            throw new BusinessException("该 PO 已全部到货入库，请勿重复操作");
        }
        List<PurchaseOrderItem> items = listItems(id);
        if (items.isEmpty()) {
            throw new BusinessException("PO 无明细，无法入库");
        }
        if (receipt == null || receipt.getItems() == null || receipt.getItems().isEmpty()) {
            throw new BusinessException("请填写本次到货数量");
        }
        Map<Long, BigDecimal> receivedByItemId = new HashMap<>();
        for (PoReceiptItemDTO line : receipt.getItems()) {
            if (line == null || line.getItemId() == null || line.getReceivedQty() == null || line.getReceivedQty().signum() < 0) {
                throw new BusinessException("到货明细不合法");
            }
            receivedByItemId.merge(line.getItemId(), line.getReceivedQty(), BigDecimal::add);
        }
        int n = 0;
        boolean hasReceipt = false;
        for (PurchaseOrderItem it : items) {
            BigDecimal delta = receivedByItemId.getOrDefault(it.getId(), BigDecimal.ZERO);
            if (delta.signum() == 0) {
                continue;
            }
            hasReceipt = true;
            if (!StringUtils.hasText(it.getProdCode()) || it.getQty() == null || it.getQty().signum() <= 0) {
                throw new BusinessException("PO 明细缺少有效产品代码或数量");
            }
            BigDecimal received = zero(it.getReceivedQty());
            BigDecimal remaining = it.getQty().subtract(received);
            if (delta.compareTo(remaining) > 0) {
                throw new BusinessException("产品 " + it.getProdCode() + " 本次到货数量超过剩余待到货数量 "
                        + remaining.stripTrailingZeros().toPlainString());
            }
            stockService.recordMovement(it.getProdCode(), it.getProductId(), it.getProductName(), "IN", delta, "PURCHASE_IN",
                    po.getPoNo(), LocalDate.now(), "PO " + po.getPoNo() + " 到货入库");
            it.setReceivedQty(received.add(delta));
            itemMapper.updateById(it);
            n++;
        }
        if (!hasReceipt || n == 0) {
            throw new BusinessException("请至少填写一项大于 0 的到货数量");
        }
        boolean completed = listItems(id).stream().allMatch(item ->
                item.getQty() != null && zero(item.getReceivedQty()).compareTo(item.getQty()) >= 0);
        po.setStatus(completed ? "COMPLETED" : "IN_PROGRESS");
        this.updateById(po);
    }

    @Override
    public IPage<PurchaseOrder> pageQuery(PageQuery query, Long purchaserId) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .orderByDesc(PurchaseOrder::getCreateTime);
        Long currentUserId = accessGuard.restrictOwnerId();
        if (currentUserId != null) {
            List<Long> piIds = accessGuard.visiblePiIdsForCurrentUser();
            wrapper.and(w -> {
                if (!piIds.isEmpty()) {
                    w.in(PurchaseOrder::getPiId, piIds).or();
                }
                w.isNull(PurchaseOrder::getPiId).eq(PurchaseOrder::getCreateBy, currentUserId);
            });
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PurchaseOrder::getPoNo, query.getKeyword())
                    .or().like(PurchaseOrder::getPiNo, query.getKeyword())
                    .or().like(PurchaseOrder::getSupplier, query.getKeyword()));
        }
        wrapper.eq(purchaserId != null, PurchaseOrder::getPurchaserId, purchaserId);
        IPage<PurchaseOrder> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        fillPurchaseSummaries(page.getRecords());
        hydratePurchasers(page.getRecords());
        return page;
    }

    @Override
    public List<PurchaserOption> purchaserOptions() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getUsername))
                .stream().map(user -> new PurchaserOption(user.getId(), firstText(user.getNickname(), user.getRealName(), user.getUsername())))
                .toList();
    }

    @Override
    public PurchaseOrderDTO getDetail(Long id) {
        PurchaseOrder po = this.getById(id);
        if (po == null) {
            throw new BusinessException("采购订单不存在");
        }
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        BeanUtils.copyProperties(po, dto);
        List<PurchaseOrderItem> items = listItems(id);
        hydrateItems(items);
        dto.setItems(items);
        hydratePurchasers(List.of(dto));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePo(PurchaseOrderDTO dto) {
        validateReferences(dto);
        if (!StringUtils.hasText(dto.getPoNo())) {
            dto.setPoNo(nextPoNo());
        }
        checkNoUnique(dto.getPoNo(), null);
        PurchaseOrder po = new PurchaseOrder();
        BeanUtils.copyProperties(dto, po);
        po.setId(null);
        po.setCreateBy(accessGuard.currentUserId());
        if (po.getPurchaserId() == null) {
            po.setPurchaserId(accessGuard.currentUserId());
        }
        po.setStatus("DRAFT");
        po.setTotalAmount(calcAndFillItems(dto.getItems()));
        this.save(po);
        saveItems(po.getId(), dto.getItems());
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePo(PurchaseOrderDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        PurchaseOrder existing = this.getById(dto.getId());
        if (existing == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态的 PO 可以修改；已确认的 PO 请通过到货入库推进状态");
        }
        validateReferences(dto);
        checkNoUnique(dto.getPoNo(), dto.getId());
        PurchaseOrder po = new PurchaseOrder();
        BeanUtils.copyProperties(dto, po);
        po.setCreateBy(existing.getCreateBy());
        po.setStatus("DRAFT");
        po.setTotalAmount(calcAndFillItems(dto.getItems()));
        this.updateById(po);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, dto.getId()));
        saveItems(dto.getId(), dto.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePo(Long id) {
        PurchaseOrder existing = this.getById(id);
        if (existing != null && !isDeletableStatus(existing.getStatus())) {
            throw new BusinessException("PO 仅草稿或已完成状态可以删除；已确认、部分入库等中间状态不能删除");
        }
        if (existing != null && java.util.Set.of("COMPLETED", "RECEIVED").contains(existing.getStatus())) {
            payableService.assertSettledBeforeDelete(existing);
        }
        this.removeById(id);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, id));
    }

    private boolean isDeletableStatus(String status) {
        return !StringUtils.hasText(status) || java.util.Set.of("DRAFT", "COMPLETED", "RECEIVED").contains(status);
    }

    @Override
    public PurchaseOrder getById(Serializable id) {
        PurchaseOrder po = super.getById(id);
        assertVisible(po);
        return po;
    }

    @Override
    public boolean removeById(Serializable id) {
        assertVisible(super.getById(id));
        return super.removeById(id);
    }

    @Override
    public List<PurchaseOrderItem> listItems(Long poId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getPoId, poId).orderByAsc(PurchaseOrderItem::getId));
    }

    private BigDecimal calcAndFillItems(List<PurchaseOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        if (items == null) {
            return total;
        }
        for (PurchaseOrderItem item : items) {
            if (item.getProductId() != null) {
                Product product = productMapper.selectById(item.getProductId());
                if (product != null) {
                    if (!StringUtils.hasText(item.getProductName())) {
                        item.setProductName(firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn()));
                    }
                    if (!StringUtils.hasText(item.getProdCode())) {
                        item.setProdCode(firstText(product.getCompanyModel(), product.getProdCode()));
                    }
                }
            }
            BigDecimal qty = item.getQty() != null ? item.getQty() : BigDecimal.ZERO;
            BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal amount = qty.multiply(price);
            item.setAmount(amount);
            total = total.add(amount);
        }
        return total;
    }

    private void validateReferences(PurchaseOrderDTO dto) {
        if (dto != null && dto.getPurchaserId() != null && userMapper.selectById(dto.getPurchaserId()) == null) {
            throw new BusinessException("采购人不存在");
        }
        if (dto != null && dto.getPiId() != null) {
            accessGuard.assertPiVisible(dto.getPiId());
            SalesPi pi = salesPiMapper.selectById(dto.getPiId());
            if (pi == null) {
                throw new BusinessException("PI 不存在");
            }
            if (!java.util.Set.of("ORDERED", "SHIPPED", "COMPLETED", "CLOSED").contains(pi.getStatus())) {
                throw new BusinessException("PI 收到定金并进入已下单状态后才能创建 PO");
            }
            dto.setPiNo(pi.getPiNo());
        }
    }

    private void assertVisible(PurchaseOrder po) {
        if (po == null) {
            return;
        }
        accessGuard.assertRecordVisible(po.getPiId(), null, po.getCreateBy(), "PO采购订单");
    }

    private void saveItems(Long poId, List<PurchaseOrderItem> items) {
        if (items == null) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            item.setId(null);
            item.setPoId(poId);
            itemMapper.insert(item);
        }
    }

    private void checkNoUnique(String poNo, Long excludeId) {
        if (!StringUtils.hasText(poNo)) {
            throw new BusinessException("PO 单号不能为空");
        }
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getPoNo, poNo);
        if (excludeId != null) {
            wrapper.ne(PurchaseOrder::getId, excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("PO 单号已存在: " + poNo);
        }
    }

    private String nextPoNo() {
        String base = "PO-" + LocalDateTime.now().format(PO_NO_TIME);
        for (int i = 0; i < 100; i++) {
            String no = i == 0 ? base : base + "-" + String.format("%02d", i + 1);
            if (this.count(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getPoNo, no)) == 0) {
                return no;
            }
        }
        throw new BusinessException("PO单号生成失败, 请稍后重试");
    }

    private void fillPurchaseSummaries(List<PurchaseOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (PurchaseOrder order : orders) {
            List<PurchaseOrderItem> items = listItems(order.getId());
            hydrateItems(items);
            String summary = items.stream()
                    .filter(item -> StringUtils.hasText(item.getProdCode()) || item.getQty() != null)
                    .map(item -> firstText(item.getProductName(), item.getProdCode(), item.getProductId() != null ? String.valueOf(item.getProductId()) : "")
                            + " x " + formatQty(item.getQty()))
                    .collect(Collectors.joining("; "));
            order.setPurchaseSummary(summary);
        }
    }

    private String formatQty(BigDecimal qty) {
        return qty == null ? "0" : qty.stripTrailingZeros().toPlainString();
    }

    private void hydratePurchasers(List<? extends PurchaseOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> ids = orders.stream().map(PurchaseOrder::getPurchaserId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> firstText(user.getNickname(), user.getRealName(), user.getUsername()), (a, b) -> a));
        orders.forEach(po -> po.setPurchaserName(names.get(po.getPurchaserId())));
    }

    private void hydrateItems(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> productIds = items.stream().map(PurchaseOrderItem::getProductId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (a, b) -> a));
        for (PurchaseOrderItem item : items) {
            Product product = products.get(item.getProductId());
            if (product != null && !StringUtils.hasText(item.getProductName())) {
                item.setProductName(firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn()));
            }
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

}
