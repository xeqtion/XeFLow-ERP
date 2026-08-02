package com.erp.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataAccessGuard;
import com.erp.common.security.DataScopeHelper;
import com.erp.finance.service.PiPaymentService;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.inventory.service.StockService;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.service.NotificationService;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.mapper.PurchaseOrderMapper;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.dto.SalesPiDTO;
import com.erp.sales.entity.ContractPrice;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.mapper.ContractPriceMapper;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.shipment.entity.Shipment;
import com.erp.shipment.entity.ShipmentItem;
import com.erp.shipment.mapper.ShipmentMapper;
import com.erp.shipment.mapper.ShipmentItemMapper;
import com.erp.task.service.PersonalTaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesPiServiceImpl extends ServiceImpl<SalesPiMapper, SalesPi> implements SalesPiService {

    private static final SecureRandom PI_RANDOM = new SecureRandom();
    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final String SOURCE_SALES_QUOTE = "SALES_QUOTE";
    private static final String SOURCE_STANDARD_PRICE = "STANDARD_PRICE";
    private static final String SOURCE_DISCOUNT_PRICE = "DISCOUNT_PRICE";
    private static final String SOURCE_FLOOR_PRICE = "FLOOR_PRICE";

    private final SalesPiItemMapper itemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final ShippingInstructionMapper shippingInstructionMapper;
    private final StockService stockService;
    private final DataScopeHelper dataScope;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final ContractPriceMapper contractPriceMapper;
    private final StandardPriceService standardPriceService;
    private final DataAccessGuard accessGuard;
    private final PersonalTaskService personalTaskService;
    private final PiPaymentService piPaymentService;
    private final NotificationService notificationService;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final FinanceEntryMapper financeEntryMapper;

    public SalesPiServiceImpl(SalesPiItemMapper itemMapper,
                              ShipmentMapper shipmentMapper,
                              ShipmentItemMapper shipmentItemMapper,
                              ShippingInstructionMapper shippingInstructionMapper,
                              StockService stockService,
                              DataScopeHelper dataScope,
                              CustomerMapper customerMapper,
                              ProductMapper productMapper,
                              ContractPriceMapper contractPriceMapper,
                              StandardPriceService standardPriceService,
                              DataAccessGuard accessGuard,
                              PersonalTaskService personalTaskService,
                              PiPaymentService piPaymentService,
                              NotificationService notificationService,
                              PurchaseOrderMapper purchaseOrderMapper,
                              FinanceEntryMapper financeEntryMapper) {
        this.itemMapper = itemMapper;
        this.shipmentMapper = shipmentMapper;
        this.shipmentItemMapper = shipmentItemMapper;
        this.shippingInstructionMapper = shippingInstructionMapper;
        this.stockService = stockService;
        this.dataScope = dataScope;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.contractPriceMapper = contractPriceMapper;
        this.standardPriceService = standardPriceService;
        this.accessGuard = accessGuard;
        this.personalTaskService = personalTaskService;
        this.piPaymentService = piPaymentService;
        this.notificationService = notificationService;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.financeEntryMapper = financeEntryMapper;
    }

    @Override
    public IPage<SalesPi> pageQuery(PageQuery query, Long requestedOwnerId) {
        Long scopedOwnerId = dataScope.restrictOwnerId();
        Long ownerId = scopedOwnerId != null ? scopedOwnerId : requestedOwnerId;
        LambdaQueryWrapper<SalesPi> wrapper = new LambdaQueryWrapper<SalesPi>()
                .eq(ownerId != null, SalesPi::getOwnerId, ownerId)
                .orderByDesc(SalesPi::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(SalesPi::getPiNo, query.getKeyword());
        }
        IPage<SalesPi> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        hydrateCustomers(page.getRecords());
        hydrateShipmentProgress(page.getRecords());
        return page;
    }

    @Override
    public SalesPiDTO getDetail(Long id) {
        SalesPi pi = this.getById(id);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        assertVisible(pi);
        SalesPiDTO dto = new SalesPiDTO();
        hydrateShipmentProgress(pi);
        BeanUtils.copyProperties(pi, dto);
        hydrateCustomer(dto);
        List<SalesPiItem> items = listItems(id);
        hydrateItems(items);
        dto.setItems(items);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePi(SalesPiDTO dto) {
        assertVisible(this.getById(dto.getId()));
        accessGuard.assertCustomerVisible(dto.getCustomerId());
        SalesPi pi = new SalesPi();
        BeanUtils.copyProperties(dto, pi);
        pi.setId(null);
        if (!accessGuard.viewAll()) {
            pi.setOwnerId(accessGuard.currentUserId());
        }
        preparePiNo(pi);
        checkPiNoUnique(pi.getPiNo(), null);
        pi.setStatus("DRAFT");
        if (pi.getDepositAmount() == null) {
            pi.setDepositAmount(BigDecimal.ZERO);
        }
        pi.setTotalAmount(prepareAndCalcItems(pi, dto.getItems()));
        validateDeposit(pi);
        this.save(pi);
        saveItems(pi.getId(), dto.getItems());
        syncPiDeliveryTask(pi);
        return pi.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePi(SalesPiDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        SalesPi existing = this.getById(dto.getId());
        if (existing == null) {
            throw new BusinessException("PI 不存在");
        }
        assertVisible(existing);
        accessGuard.assertCustomerVisible(dto.getCustomerId());
        SalesPi pi = new SalesPi();
        BeanUtils.copyProperties(dto, pi);
        pi.setCreateBy(existing.getCreateBy());
        pi.setStatus(existing.getStatus());
        if (!accessGuard.viewAll() || pi.getOwnerId() == null) {
            pi.setOwnerId(existing.getOwnerId());
        }
        preparePiNo(pi);
        checkPiNoUnique(pi.getPiNo(), dto.getId());
        if (pi.getDepositAmount() == null) {
            pi.setDepositAmount(BigDecimal.ZERO);
        }
        pi.setTotalAmount(prepareAndCalcItems(pi, dto.getItems()));
        validateDeposit(pi);
        this.updateById(pi);
        itemMapper.delete(new LambdaQueryWrapper<SalesPiItem>().eq(SalesPiItem::getPiId, dto.getId()));
        saveItems(dto.getId(), dto.getItems());
        syncPiDeliveryTask(this.getById(dto.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePi(Long id) {
        SalesPi pi = this.getById(id);
        assertVisible(pi);
        if (pi != null && !isDeletableStatus(pi.getStatus())) {
            throw new BusinessException("PI 仅草稿或已完成状态可以删除；已确认、已下单、部分出货等中间状态不能删除");
        }
        this.removeById(id);
        itemMapper.delete(new LambdaQueryWrapper<SalesPiItem>().eq(SalesPiItem::getPiId, id));
        personalTaskService.removeSystemTask("PI_DELIVERY", id);
    }

    private boolean isDeletableStatus(String status) {
        return !StringUtils.hasText(status) || Set.of("DRAFT", "COMPLETED", "CLOSED").contains(status);
    }

    @Override
    public List<SalesPiItem> listItems(Long piId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<SalesPiItem>().eq(SalesPiItem::getPiId, piId).orderByAsc(SalesPiItem::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        SalesPi pi = this.getById(id);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        assertVisible(pi);
        if ("CONFIRMED".equals(pi.getStatus())) {
            return;
        }
        if (!"DRAFT".equals(pi.getStatus())) {
            throw new BusinessException("只有草稿状态的 PI 可以确认");
        }
        validateItemsForConfirmation(listItems(id));
        pi.setStatus(zero(pi.getDepositAmount()).signum() <= 0 ? "ORDERED" : "CONFIRMED");
        this.updateById(pi);
        if ("CONFIRMED".equals(pi.getStatus())) {
            notifyFinanceToCollectDeposit(pi);
        }
    }

    private void validateItemsForConfirmation(List<SalesPiItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("确认 PI 前请至少添加一条产品明细");
        }
        for (int i = 0; i < items.size(); i++) {
            SalesPiItem item = items.get(i);
            int rowNo = i + 1;
            if (item.getProductId() == null || !StringUtils.hasText(item.getProdCode())) {
                throw new BusinessException("第 " + rowNo + " 条 PI 明细缺少有效产品");
            }
            if (item.getQty() == null || item.getQty().signum() <= 0) {
                throw new BusinessException("第 " + rowNo + " 条 PI 明细数量必须大于 0");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new BusinessException("第 " + rowNo + " 条 PI 明细单价不能为空且不能为负数");
            }
        }
    }

    @Scheduled(fixedDelayString = "${erp.sales-pi.draft-cleanup-delay-ms:60000}",
            initialDelayString = "${erp.sales-pi.draft-cleanup-initial-delay-ms:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredDrafts() {
        List<SalesPi> expired = this.list(new LambdaQueryWrapper<SalesPi>()
                .eq(SalesPi::getStatus, "DRAFT")
                .isNotNull(SalesPi::getCreateTime)
                .lt(SalesPi::getCreateTime, LocalDateTime.now().minusHours(2)));
        for (SalesPi pi : expired) {
            removePi(pi.getId());
        }
    }

    @SuppressWarnings("unused")
    private Long generateShipmentLegacy(Long piId, BigDecimal qty, LocalDate shipDate) {
        SalesPi pi = this.getById(piId);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        assertVisible(pi);
        if ("DRAFT".equals(pi.getStatus()) || !StringUtils.hasText(pi.getStatus())) {
            throw new BusinessException("请先确认 PI 后再生成出货单");
        }
        piPaymentService.assertFullyPaid(pi);
        // PI 总数量(明细数量之和) 与 累计已出货量
        BigDecimal totalQty = BigDecimal.ZERO;
        for (SalesPiItem it : listItems(piId)) {
            totalQty = totalQty.add(it.getQty() != null ? it.getQty() : BigDecimal.ZERO);
        }
        BigDecimal shipped = shippedQty(piId);
        BigDecimal remaining = totalQty.subtract(shipped);
        if (remaining.signum() <= 0) {
            throw new BusinessException("该 PI 已全部出货, 无可出数量");
        }
        BigDecimal shipQty = (qty == null || qty.signum() <= 0) ? remaining : qty;
        if (shipQty.compareTo(remaining) > 0) {
            throw new BusinessException("出货数量 " + shipQty.stripTrailingZeros().toPlainString()
                    + " 超过 PI 剩余可出量 " + remaining.stripTrailingZeros().toPlainString());
        }

        Shipment s = new Shipment();
        s.setShipmentNo("SHP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + (System.currentTimeMillis() % 100000));
        s.setPiId(piId);
        s.setShipDate(shipDate != null ? shipDate : LocalDate.now());
        s.setQty(shipQty);
        s.setStatus("DRAFT");
        s.setRemark("由 PI " + pi.getPiNo() + " 生成");
        shipmentMapper.insert(s);
        syncShipmentTask(pi, s);

        // 累计已出货达到总量则将 PI 置为已出货
        return s.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateShipment(Long piId, BigDecimal qty, LocalDate shipDate) {
        SalesPi pi = this.getById(piId);
        if (pi == null) {
            throw new BusinessException("PI不存在");
        }
        assertVisible(pi);
        if (!Set.of("CONFIRMED", "ORDERED", "SHIPPED").contains(pi.getStatus())) {
            throw new BusinessException("当前PI状态不能生成出货单");
        }
        piPaymentService.assertFullyPaid(pi);

        List<SalesPiItem> piItems = listItems(piId);
        if (piItems.isEmpty()) {
            throw new BusinessException("PI无明细，无法生成出货单");
        }
        Map<Long, BigDecimal> shippedByItem = shippedQtyByPiItem(piId);
        List<ShipmentAllocation> candidates = new ArrayList<>();
        BigDecimal remainingTotal = BigDecimal.ZERO;
        BigDecimal availableTotal = BigDecimal.ZERO;
        for (SalesPiItem item : piItems) {
            BigDecimal remaining = zero(item.getQty()).subtract(shippedByItem.getOrDefault(item.getId(), BigDecimal.ZERO));
            if (remaining.signum() <= 0) {
                continue;
            }
            remainingTotal = remainingTotal.add(remaining);
            if (!StringUtils.hasText(item.getProdCode())) {
                throw new BusinessException("PI明细缺少产品代码，无法校验库存");
            }
            BigDecimal available = remaining.min(stockService.currentStock(item.getProdCode()).max(BigDecimal.ZERO));
            if (available.signum() > 0) {
                candidates.add(new ShipmentAllocation(item, available));
                availableTotal = availableTotal.add(available);
            }
        }
        if (remainingTotal.signum() <= 0) {
            throw new BusinessException("该PI已全部出货，无可出数量");
        }
        if (availableTotal.signum() <= 0) {
            throw new BusinessException("当前库存不足，暂无可出货数量");
        }
        BigDecimal requested = qty == null || qty.signum() <= 0 ? availableTotal : qty;
        if (requested.compareTo(availableTotal) > 0) {
            throw new BusinessException("当前库存最多可出" + formatQty(availableTotal) + "，请输入不超过该数量的本次出货数量");
        }

        BigDecimal leftToAllocate = requested;
        List<ShipmentAllocation> allocations = new ArrayList<>();
        for (ShipmentAllocation candidate : candidates) {
            if (leftToAllocate.signum() <= 0) {
                break;
            }
            BigDecimal allocated = candidate.available().min(leftToAllocate);
            if (allocated.signum() > 0) {
                allocations.add(new ShipmentAllocation(candidate.item(), allocated));
                leftToAllocate = leftToAllocate.subtract(allocated);
            }
        }
        BigDecimal actualQty = requested.subtract(leftToAllocate);
        boolean fullyShipped = remainingTotal.subtract(actualQty).signum() <= 0;

        Shipment shipment = new Shipment();
        shipment.setShipmentNo(nextShipmentNo());
        shipment.setSiId(findShippingInstructionId(piId));
        shipment.setPiId(piId);
        shipment.setShipDate(shipDate != null ? shipDate : LocalDate.now());
        shipment.setQty(actualQty);
        shipment.setStatus(fullyShipped ? "FULL_SHIPPED" : "PARTIAL_SHIPPED");
        shipment.setRemark("由PI " + pi.getPiNo() + " 生成；本次出货：" + allocationSummary(allocations)
                + "；剩余未出货 " + formatQty(remainingTotal.subtract(actualQty)));
        shipment.setCreateBy(accessGuard.currentUserId());
        shipmentMapper.insert(shipment);

        for (ShipmentAllocation allocation : allocations) {
            SalesPiItem item = allocation.item();
            ShipmentItem shipmentItem = new ShipmentItem();
            shipmentItem.setShipmentId(shipment.getId());
            shipmentItem.setPiItemId(item.getId());
            shipmentItem.setProductId(item.getProductId());
            shipmentItem.setProdCode(item.getProdCode());
            shipmentItem.setQty(allocation.available());
            shipmentItemMapper.insert(shipmentItem);
            stockService.recordMovement(item.getProdCode(), "OUT", allocation.available(), "SALE_OUT",
                    shipment.getShipmentNo(), shipment.getShipDate(), "PI " + pi.getPiNo() + " 出货");
        }
        pi.setStatus(fullyShipped ? "COMPLETED" : "SHIPPED");
        this.updateById(pi);
        syncShipmentTask(pi, shipment);
        if (fullyShipped) {
            notifyFinanceToPayPurchaseOrders(pi);
        }
        return shipment.getId();
    }

    private void notifyFinanceToCollectDeposit(SalesPi pi) {
        BigDecimal deposit = zero(pi.getDepositAmount());
        if (pi == null || pi.getId() == null || deposit.signum() <= 0) {
            return;
        }
        String customer = customerName(pi);
        String title = "PI 收定金待办";
        String content = "PI 合同 " + firstNonBlank(pi.getPiNo(), "-")
                + " 已确认，客户：" + customer
                + "，待收定金：" + formatQty(deposit) + " " + firstNonBlank(pi.getCurrency(), "")
                + "。请在应收账款中登记定金收款。";
        notificationService.createForRolesOnce(Set.of("finance"), title, content, NotificationType.SYSTEM,
                "finance-deposit-pi-" + pi.getId());
    }

    private void notifyFinanceToPayPurchaseOrders(SalesPi pi) {
        if (pi == null || pi.getId() == null) {
            return;
        }
        List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getPiId, pi.getId())
                .in(PurchaseOrder::getStatus, "CONFIRMED", "IN_PROGRESS", "COMPLETED", "RECEIVED"));
        if (purchaseOrders.isEmpty()) {
            return;
        }
        List<String> poNos = purchaseOrders.stream().map(PurchaseOrder::getPoNo)
                .filter(StringUtils::hasText).toList();
        Map<String, BigDecimal> paidByPo = financeEntryMapper.selectList(new LambdaQueryWrapper<FinanceEntry>()
                        .eq(FinanceEntry::getRelatedDocType, "PO")
                        .in(!poNos.isEmpty(), FinanceEntry::getRelatedDocNo, poNos)
                        .eq(FinanceEntry::getDirection, "out"))
                .stream()
                .filter(entry -> StringUtils.hasText(entry.getRelatedDocNo()))
                .collect(Collectors.groupingBy(FinanceEntry::getRelatedDocNo,
                        Collectors.mapping(entry -> zero(entry.getAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        List<PurchaseOrder> unpaidOrders = purchaseOrders.stream()
                .filter(po -> zero(po.getTotalAmount()).compareTo(paidByPo.getOrDefault(po.getPoNo(), BigDecimal.ZERO)) > 0)
                .toList();
        if (unpaidOrders.isEmpty()) {
            return;
        }
        String poSummary = unpaidOrders.stream()
                .map(po -> po.getPoNo() + "（待付 " + formatQty(zero(po.getTotalAmount())
                        .subtract(paidByPo.getOrDefault(po.getPoNo(), BigDecimal.ZERO))) + " "
                        + firstNonBlank(po.getCurrency(), "") + "）")
                .collect(Collectors.joining("、"));
        String title = "PO 付款待办";
        String content = "PI 合同 " + firstNonBlank(pi.getPiNo(), "-") + " 已交货，关联 PO 尚有应付账款："
                + poSummary + "。请在应付账款中登记付款。";
        notificationService.createForRolesOnce(Set.of("finance"), title, content, NotificationType.SYSTEM,
                "finance-payable-pi-" + pi.getId());
    }

    private String customerName(SalesPi pi) {
        if (pi.getCustomerId() == null) {
            return "-";
        }
        Customer customer = customerMapper.selectById(pi.getCustomerId());
        return customer == null ? "-" : firstNonBlank(customer.getCompanyName(), customer.getCustNameEn(),
                customer.getCustNameCn(), customer.getCustCode(), "-");
    }

    private void syncPiDeliveryTask(SalesPi pi) {
        if (pi == null || pi.getId() == null) {
            return;
        }
        String title = "PI交货: " + firstNonBlank(pi.getPiNo(), "未命名PI");
        String content = "交货日期 " + pi.getDeliveryDate()
                + "\n客户: " + firstNonBlank(pi.getCustomerName(), pi.getCustomerCode(), "-")
                + "\n金额: " + (pi.getTotalAmount() == null ? "-" : pi.getTotalAmount().stripTrailingZeros().toPlainString())
                + " " + firstNonBlank(pi.getCurrency(), "");
        personalTaskService.upsertSystemTask(resolvePiOwner(pi), title, content, pi.getDeliveryDate(),
                "HIGH", "PI_DELIVERY", "PI_DELIVERY", pi.getId(), pi.getPiNo());
    }

    private void syncShipmentTask(SalesPi pi, Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            return;
        }
        String title = "出货单: " + firstNonBlank(shipment.getShipmentNo(), "未命名单据");
        String content = "由 PI " + firstNonBlank(pi != null ? pi.getPiNo() : null, "-") + " 生成"
                + "\n出货日期 " + shipment.getShipDate()
                + "\n数量: " + (shipment.getQty() == null ? "-" : shipment.getQty().stripTrailingZeros().toPlainString());
        personalTaskService.upsertSystemTask(resolvePiOwner(pi), title, content, shipment.getShipDate(),
                "HIGH", "SHIPMENT", "SHIPMENT", shipment.getId(), shipment.getShipmentNo());
    }

    private Long resolvePiOwner(SalesPi pi) {
        if (pi == null) {
            return accessGuard.currentUserId();
        }
        return pi.getOwnerId() != null ? pi.getOwnerId() : pi.getCreateBy();
    }

    /** PI 累计已出货量 */
    private Map<Long, BigDecimal> shippedQtyByPiItem(Long piId) {
        List<Shipment> shipments = shipmentMapper.selectList(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getPiId, piId)
                .in(Shipment::getStatus, "PARTIAL_SHIPPED", "FULL_SHIPPED"));
        if (shipments.isEmpty()) {
            return Map.of();
        }
        List<Long> shipmentIds = shipments.stream().map(Shipment::getId).toList();
        return shipmentItemMapper.selectList(new LambdaQueryWrapper<ShipmentItem>()
                        .in(ShipmentItem::getShipmentId, shipmentIds))
                .stream()
                .filter(item -> item.getPiItemId() != null)
                .collect(Collectors.groupingBy(ShipmentItem::getPiItemId,
                        Collectors.mapping(item -> zero(item.getQty()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private void hydrateShipmentProgress(List<SalesPi> pis) {
        for (SalesPi pi : pis) {
            hydrateShipmentProgress(pi);
        }
    }

    private void hydrateShipmentProgress(SalesPi pi) {
        if (pi == null || pi.getId() == null) {
            return;
        }
        BigDecimal total = listItems(pi.getId()).stream().map(item -> zero(item.getQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipped = shippedQtyByPiItem(pi.getId()).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        pi.setShippedQty(shipped);
        pi.setRemainingShipQty(total.subtract(shipped).max(BigDecimal.ZERO));
    }

    private Long findShippingInstructionId(Long piId) {
        return shippingInstructionMapper.selectList(new LambdaQueryWrapper<ShippingInstruction>()
                        .eq(ShippingInstruction::getPiId, piId)
                        .orderByDesc(ShippingInstruction::getCreateTime)
                        .last("LIMIT 1"))
                .stream().findFirst().map(ShippingInstruction::getId).orElse(null);
    }

    private String nextShipmentNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 100; i++) {
            String shipmentNo = "SHP" + date + "-" + String.format("%05d", PI_RANDOM.nextInt(100000));
            if (shipmentMapper.selectCount(new LambdaQueryWrapper<Shipment>()
                    .eq(Shipment::getShipmentNo, shipmentNo)) == 0) {
                return shipmentNo;
            }
        }
        throw new BusinessException("出货单号生成失败，请稍后重试");
    }

    private String allocationSummary(List<ShipmentAllocation> allocations) {
        return allocations.stream().map(allocation -> allocation.item().getProdCode() + " x "
                        + formatQty(allocation.available()))
                .collect(Collectors.joining("；"));
    }

    private String formatQty(BigDecimal qty) {
        return zero(qty).stripTrailingZeros().toPlainString();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ShipmentAllocation(SalesPiItem item, BigDecimal available) {
    }

    private BigDecimal shippedQty(Long piId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Shipment s : shipmentMapper.selectList(
                new LambdaQueryWrapper<Shipment>().eq(Shipment::getPiId, piId))) {
            sum = sum.add(s.getQty() != null ? s.getQty() : BigDecimal.ZERO);
        }
        return sum;
    }

    /** 补齐 PI 明细产品/报价快照, 计算每行金额并返回合计。 */
    private BigDecimal prepareAndCalcItems(SalesPi pi, List<SalesPiItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        if (items == null) {
            normalizePiCurrency(pi, null);
            return total;
        }
        String currencyCandidate = null;
        Iterator<SalesPiItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            SalesPiItem item = iterator.next();
            if (isBlankItem(item)) {
                iterator.remove();
                continue;
            }
            currencyCandidate = firstNonBlank(currencyCandidate, prepareItem(pi, item));
            validateItem(item);
            BigDecimal qty = item.getQty();
            BigDecimal price = item.getUnitPrice();
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : qty.multiply(price);
            item.setAmount(amount);
            total = total.add(amount);
        }
        normalizePiCurrency(pi, currencyCandidate);
        return total;
    }

    private String prepareItem(SalesPi pi, SalesPiItem item) {
        if (item.getProductId() == null) {
            throw new BusinessException("PI 明细产品不能为空");
        }
        Product product = productMapper.selectById(item.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: " + item.getProductId());
        }

        item.setProductName(firstNonBlank(item.getProductName(), product.getProductName(),
                product.getProdNameCn(), product.getProdNameEn()));
        item.setProductModel(firstNonBlank(item.getProductModel(), product.getCompanyModel(),
                product.getProdCode(), product.getSpec()));
        item.setFactoryModel(firstNonBlank(item.getFactoryModel(), product.getFactoryModel()));
        item.setUnit(quantityUnit(firstNonBlank(item.getUnit(), product.getUnit())));
        item.setProdCode(firstNonBlank(item.getProdCode(), product.getProdCode()));

        String currencyCandidate = null;
        normalizeQuoteSource(item);
        ContractPrice quote = shouldUseSalesQuote(item) ? findApprovedQuote(pi.getCustomerId(), item.getProductId(), item.getQuoteId()) : null;
        if (quote != null) {
            item.setQuoteId(quote.getId());
            item.setQuoteSource(SOURCE_SALES_QUOTE);
            item.setProductName(firstNonBlank(item.getProductName(), quote.getProductName()));
            item.setProductModel(firstNonBlank(item.getProductModel(), quote.getProductModel()));
            item.setProdCode(firstNonBlank(item.getProdCode(), quote.getProdCode()));
            if (item.getUnitPrice() == null || SOURCE_SALES_QUOTE.equals(item.getQuoteSource())) {
                item.setUnitPrice(quote.getContractPrice());
            }
            if (item.getStandardPrice() == null) {
                item.setStandardPrice(quote.getStandardPrice());
            }
            currencyCandidate = quote.getCurrency();
        }

        StandardPrice standardPrice = standardPriceService.findApplicable(item.getProductId(), pi.getCustomerId());
        if (standardPrice != null) {
            if (item.getStandardPrice() == null) {
                item.setStandardPrice(standardPrice.getStandardUnitPrice());
            }
            if (item.getDiscountUnitPrice() == null) {
                item.setDiscountUnitPrice(standardPrice.getDiscountUnitPrice());
            }
            if (item.getFloorPrice() == null) {
                item.setFloorPrice(standardPrice.getFloorPrice());
            }
            if (SOURCE_STANDARD_PRICE.equals(item.getQuoteSource())) {
                item.setQuoteId(null);
                item.setUnitPrice(standardPrice.getStandardUnitPrice());
            } else if (SOURCE_DISCOUNT_PRICE.equals(item.getQuoteSource())) {
                item.setQuoteId(null);
                item.setUnitPrice(standardPrice.getDiscountUnitPrice());
            } else if (SOURCE_FLOOR_PRICE.equals(item.getQuoteSource())) {
                item.setQuoteId(null);
                item.setUnitPrice(standardPrice.getFloorPrice());
            } else if (item.getUnitPrice() == null) {
                item.setUnitPrice(firstPrice(standardPrice.getDiscountUnitPrice(), standardPrice.getStandardUnitPrice()));
            }
            currencyCandidate = firstNonBlank(currencyCandidate, standardPrice.getCurrency());
        }
        return firstNonBlank(currencyCandidate, product.getCurrency());
    }

    private boolean shouldUseSalesQuote(SalesPiItem item) {
        return item.getQuoteId() != null || SOURCE_SALES_QUOTE.equals(item.getQuoteSource());
    }

    private void normalizeQuoteSource(SalesPiItem item) {
        if (!StringUtils.hasText(item.getQuoteSource())) {
            item.setQuoteSource(null);
            if (item.getQuoteId() == null) {
                return;
            }
            item.setQuoteSource(SOURCE_SALES_QUOTE);
            return;
        }
        String source = item.getQuoteSource().trim().toUpperCase();
        if (!SOURCE_SALES_QUOTE.equals(source)
                && !SOURCE_STANDARD_PRICE.equals(source)
                && !SOURCE_DISCOUNT_PRICE.equals(source)
                && !SOURCE_FLOOR_PRICE.equals(source)) {
            item.setQuoteSource(null);
            item.setQuoteId(null);
            return;
        }
        item.setQuoteSource(source);
        if (!SOURCE_SALES_QUOTE.equals(source)) {
            item.setQuoteId(null);
        }
    }

    private ContractPrice findApprovedQuote(Long customerId, Long productId, Long quoteId) {
        if (quoteId != null) {
            ContractPrice quote = contractPriceMapper.selectById(quoteId);
            if (quote == null) {
                throw new BusinessException("销售报价不存在: " + quoteId);
            }
            if (!"PASS".equals(quote.getAuditStatus())) {
                throw new BusinessException("销售报价未通过审核, 不能用于 PI: " + quoteId);
            }
            if (customerId != null && quote.getCustomerId() != null && !customerId.equals(quote.getCustomerId())) {
                throw new BusinessException("销售报价客户与 PI 客户不一致: " + quoteId);
            }
            if (productId != null && quote.getProductId() != null && !productId.equals(quote.getProductId())) {
                throw new BusinessException("销售报价产品与 PI 明细产品不一致: " + quoteId);
            }
            return quote;
        }
        if (customerId == null || productId == null) {
            return null;
        }
        return contractPriceMapper.selectOne(new LambdaQueryWrapper<ContractPrice>()
                .eq(ContractPrice::getCustomerId, customerId)
                .eq(ContractPrice::getProductId, productId)
                .eq(ContractPrice::getAuditStatus, "PASS")
                .orderByDesc(ContractPrice::getCreateTime)
                .orderByDesc(ContractPrice::getId)
                .last("LIMIT 1"));
    }

    private void validateItem(SalesPiItem item) {
        if (item.getQty() == null || item.getQty().signum() <= 0) {
            throw new BusinessException("PI 明细数量必须大于 0");
        }
        if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
            throw new BusinessException("PI 明细单价不能为空且不能为负数");
        }
        if (item.getAmount() != null && item.getAmount().signum() < 0) {
            throw new BusinessException("PI 明细金额不能为负数");
        }
    }

    private boolean isBlankItem(SalesPiItem item) {
        return item == null
                || (item.getProductId() == null
                && !StringUtils.hasText(item.getProdCode())
                && !StringUtils.hasText(item.getProductName())
                && !StringUtils.hasText(item.getProductModel())
                && item.getQty() == null
                && item.getUnitPrice() == null
                && item.getAmount() == null
                && !StringUtils.hasText(item.getRemark()));
    }

    private void normalizePiCurrency(SalesPi pi, String candidate) {
        if (StringUtils.hasText(pi.getCurrency())) {
            pi.setCurrency(pi.getCurrency().trim().toUpperCase());
        } else {
            pi.setCurrency(firstNonBlank(candidate, "USD").toUpperCase());
        }
    }

    private BigDecimal firstPrice(BigDecimal... prices) {
        for (BigDecimal price : prices) {
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private void saveItems(Long piId, List<SalesPiItem> items) {
        if (items == null) {
            return;
        }
        for (SalesPiItem item : items) {
            item.setId(null);
            item.setPiId(piId);
            itemMapper.insert(item);
        }
    }

    private void hydrateItems(List<SalesPiItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> productIds = items.stream()
                .map(SalesPiItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        items.forEach(item -> {
            Product product = products.get(item.getProductId());
            if (product == null) {
                return;
            }
            item.setProductName(firstNonBlank(item.getProductName(), product.getProductName(),
                    product.getProdNameCn(), product.getProdNameEn()));
            item.setProductModel(firstNonBlank(item.getProductModel(), product.getCompanyModel(),
                    product.getProdCode(), product.getSpec()));
            item.setFactoryModel(firstNonBlank(item.getFactoryModel(), product.getFactoryModel()));
            item.setUnit(quantityUnit(firstNonBlank(item.getUnit(), product.getUnit())));
            item.setProdCode(firstNonBlank(item.getProdCode(), product.getProdCode()));
        });
    }

    private void preparePiNo(SalesPi pi) {
        if (pi.getSignDate() == null) {
            pi.setSignDate(LocalDate.now());
        }
        if (StringUtils.hasText(pi.getPiNo())) {
            pi.setPiNo(pi.getPiNo().trim().toUpperCase());
            return;
        }
        pi.setPiNo(nextPiNo(pi.getSignDate()));
    }

    private String nextPiNo(LocalDate signDate) {
        String datePart = signDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 200; i++) {
            String candidate = "XF" + datePart + randomMixedSuffix();
            if (this.count(new LambdaQueryWrapper<SalesPi>().eq(SalesPi::getPiNo, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BusinessException("PI 单号生成失败, 请稍后重试");
    }

    private String randomMixedSuffix() {
        char[] value = new char[] {
                LETTERS[PI_RANDOM.nextInt(LETTERS.length)],
                DIGITS[PI_RANDOM.nextInt(DIGITS.length)],
                ALPHANUMERIC[PI_RANDOM.nextInt(ALPHANUMERIC.length)],
                ALPHANUMERIC[PI_RANDOM.nextInt(ALPHANUMERIC.length)]
        };
        for (int i = value.length - 1; i > 0; i--) {
            int j = PI_RANDOM.nextInt(i + 1);
            char tmp = value[i];
            value[i] = value[j];
            value[j] = tmp;
        }
        return new String(value);
    }

    private void hydrateCustomers(List<SalesPi> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream()
                .map(SalesPi::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Customer> customers = customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a));
        records.forEach(pi -> fillCustomer(pi, customers.get(pi.getCustomerId())));
    }

    private void hydrateCustomer(SalesPi pi) {
        if (pi == null || pi.getCustomerId() == null) {
            return;
        }
        fillCustomer(pi, customerMapper.selectById(pi.getCustomerId()));
    }

    private void fillCustomer(SalesPi pi, Customer customer) {
        if (customer == null) {
            return;
        }
        pi.setCustomerCode(customer.getCustCode());
        pi.setCustomerName(firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(), customer.getCustCode()));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String quantityUnit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.matches("(?i)USD|CNY|EUR|GBP|JPY|HKD|RMB") ? null : text;
    }

    private void assertVisible(SalesPi pi) {
        Long ownerId = dataScope.restrictOwnerId();
        if (pi == null || ownerId == null) {
            return;
        }
        if (!ownerId.equals(pi.getOwnerId())) {
            throw new BusinessException("无权查看该 PI 单据");
        }
    }

    private void checkPiNoUnique(String piNo, Long excludeId) {
        if (!StringUtils.hasText(piNo)) {
            throw new BusinessException("PI 单号不能为空");
        }
        LambdaQueryWrapper<SalesPi> wrapper = new LambdaQueryWrapper<SalesPi>().eq(SalesPi::getPiNo, piNo);
        if (excludeId != null) {
            wrapper.ne(SalesPi::getId, excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("PI 单号已存在: " + piNo);
        }
    }

    private void validateDeposit(SalesPi pi) {
        BigDecimal deposit = pi.getDepositAmount() == null ? BigDecimal.ZERO : pi.getDepositAmount();
        BigDecimal total = pi.getTotalAmount() == null ? BigDecimal.ZERO : pi.getTotalAmount();
        if (deposit.signum() < 0) {
            throw new BusinessException("定金金额不能小于 0");
        }
        if (deposit.compareTo(total) > 0) {
            throw new BusinessException("定金金额不能超过 PI 合计金额");
        }
    }
}
