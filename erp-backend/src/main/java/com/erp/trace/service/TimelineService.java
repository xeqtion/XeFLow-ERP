package com.erp.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataScopeHelper;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.mapper.PurchaseOrderMapper;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.shipment.entity.Shipment;
import com.erp.shipment.mapper.ShipmentMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import com.erp.trace.dto.PiBrief;
import com.erp.trace.dto.PiTraceItem;
import com.erp.trace.dto.PiTraceVO;
import com.erp.trace.dto.TimelineEvent;
import com.erp.trace.dto.TraceOwnerOption;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 单据追踪 - 以 PI 为主线串起 出货指示 -> 出货单 -> 收付款 的全链路时间线。
 */
@Service
public class TimelineService {

    private final SalesPiMapper salesPiMapper;
    private final SalesPiItemMapper salesPiItemMapper;
    private final ShippingInstructionMapper siMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final ShipmentMapper shipmentMapper;
    private final FinanceEntryMapper financeMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper userMapper;
    private final DataScopeHelper dataScope;

    public TimelineService(SalesPiMapper salesPiMapper, SalesPiItemMapper salesPiItemMapper, ShippingInstructionMapper siMapper,
                           PurchaseOrderMapper purchaseOrderMapper,
                           ShipmentMapper shipmentMapper, FinanceEntryMapper financeMapper,
                           CustomerMapper customerMapper, ProductMapper productMapper,
                           SysUserMapper userMapper, DataScopeHelper dataScope) {
        this.salesPiMapper = salesPiMapper;
        this.salesPiItemMapper = salesPiItemMapper;
        this.siMapper = siMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.shipmentMapper = shipmentMapper;
        this.financeMapper = financeMapper;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.dataScope = dataScope;
    }

    /** 最近的 PI 列表 (供选择) */
    public List<PiBrief> recentPis(Long ownerId) {
        Long scopedOwnerId = dataScope.restrictOwnerId();
        Long effectiveOwnerId = scopedOwnerId != null ? scopedOwnerId : ownerId;
        List<SalesPi> pis = salesPiMapper.selectList(Wrappers.<SalesPi>lambdaQuery()
                .eq(effectiveOwnerId != null, SalesPi::getOwnerId, effectiveOwnerId)
                .orderByDesc(SalesPi::getSignDate).orderByDesc(SalesPi::getId).last("LIMIT 200"));
        Map<Long, String> ownerNames = ownerNameMap(pis.stream().map(this::resolveOwnerId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<PiBrief> result = new ArrayList<>();
        for (SalesPi pi : pis) {
            Long piOwnerId = resolveOwnerId(pi);
            result.add(new PiBrief(pi.getId(), pi.getPiNo(), customerDisplay(loadCustomer(pi.getCustomerId())),
                    dateStr(pi.getSignDate()), piOwnerId, ownerNames.get(piOwnerId)));
        }
        return result;
    }

    /** 单据追踪可筛选的合同担当。普通业务员仅返回自己。 */
    public List<TraceOwnerOption> ownerOptions() {
        Long scopedOwnerId = dataScope.restrictOwnerId();
        if (scopedOwnerId != null) {
            return List.of(new TraceOwnerOption(scopedOwnerId, ownerName(scopedOwnerId)));
        }
        List<SalesPi> rows = salesPiMapper.selectList(Wrappers.<SalesPi>lambdaQuery()
                .select(SalesPi::getOwnerId)
                .isNotNull(SalesPi::getOwnerId)
                .groupBy(SalesPi::getOwnerId)
                .orderByAsc(SalesPi::getOwnerId));
        Set<Long> ownerIds = rows.stream()
                .map(SalesPi::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> ownerNames = ownerNameMap(ownerIds);
        return ownerIds.stream()
                .map(id -> new TraceOwnerOption(id, ownerNames.getOrDefault(id, "用户#" + id)))
                .collect(Collectors.toList());
    }

    public PiTraceVO trace(Long piId) {
        SalesPi pi = salesPiMapper.selectById(piId);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        assertVisible(pi);
        Customer customer = loadCustomer(pi.getCustomerId());
        List<PiTraceItem> items = traceItems(pi.getId());
        Long piOwnerId = resolveOwnerId(pi);
        PiTraceVO vo = new PiTraceVO();
        vo.setPiId(pi.getId());
        vo.setPiNo(pi.getPiNo());
        vo.setOwnerId(piOwnerId);
        vo.setOwnerName(ownerName(piOwnerId));
        vo.setCustomerCode(customer != null ? customer.getCustCode() : null);
        vo.setCustomer(customerDisplay(customer));
        vo.setCurrency(pi.getCurrency());
        vo.setTotalAmount(pi.getTotalAmount());
        vo.setStatus(pi.getStatus());
        vo.setSignDate(dateStr(pi.getSignDate()));
        vo.setDeliveryDate(dateStr(pi.getDeliveryDate()));
        vo.setPaymentMethod(pi.getPaymentMethod());
        vo.setTradeTerm(pi.getTradeTerm());
        vo.setItems(items);

        List<TimelineEvent> events = new ArrayList<>();
        // Business sequence: PI -> deposit -> PO -> SI -> full payment -> shipment -> delivery -> completion.
        events.add(new TimelineEvent("PI", "PI明细", pi.getPiNo(), dateStr(pi.getSignDate()),
                pi.getStatus(), "合同金额 " + money(pi.getTotalAmount()) + " " + nz(pi.getCurrency())
                + "，定金 " + money(pi.getDepositAmount()) + "，明细 " + items.size() + " 项"));

        List<FinanceEntry> entries = financeMapper.selectList(Wrappers.<FinanceEntry>lambdaQuery()
                .eq(FinanceEntry::getRelatedDocType, "PI")
                .eq(FinanceEntry::getRelatedDocNo, pi.getPiNo())
                .eq(FinanceEntry::getDirection, "in")
                .orderByAsc(FinanceEntry::getEntryDate).orderByAsc(FinanceEntry::getId));
        for (FinanceEntry e : entries) {
            if (FinanceType.PRE_RECEIVE.name().equals(e.getType())) {
                events.add(new TimelineEvent("DEPOSIT", "收定金", e.getEntryNo(), dateStr(e.getEntryDate()),
                        "已收定金", money(e.getAmount()) + " " + nz(e.getCurrency())));
            }
        }

        List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.selectList(
                Wrappers.<PurchaseOrder>lambdaQuery().eq(PurchaseOrder::getPiId, pi.getId())
                        .orderByAsc(PurchaseOrder::getOrderDate).orderByAsc(PurchaseOrder::getId));
        for (PurchaseOrder po : purchaseOrders) {
            events.add(new TimelineEvent("PO", "PO采购", po.getPoNo(), dateStr(po.getOrderDate()),
                    po.getStatus(), "供应商 " + nz(po.getSupplier()) + "，金额 " + money(po.getTotalAmount())
                            + " " + nz(po.getCurrency())));
        }

        List<ShippingInstruction> sis = siMapper.selectList(
                Wrappers.<ShippingInstruction>lambdaQuery().eq(ShippingInstruction::getPiId, pi.getId())
                        .orderByAsc(ShippingInstruction::getShipDate).orderByAsc(ShippingInstruction::getId));
        for (ShippingInstruction si : sis) {
            events.add(new TimelineEvent("SI", "出货指示", si.getSiNo(), dateStr(si.getShipDate()),
                    si.getStatus(), "港口 " + nz(si.getPort())));
        }

        java.math.BigDecimal received = entries.stream().map(FinanceEntry::getAmount)
                .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (pi.getTotalAmount() != null && pi.getTotalAmount().signum() > 0 && received.compareTo(pi.getTotalAmount()) >= 0) {
            FinanceEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            events.add(new TimelineEvent("FULL_PAYMENT", "收全款", last == null ? pi.getPiNo() : last.getEntryNo(),
                    last == null ? null : dateStr(last.getEntryDate()), "已收全款",
                    money(received) + " " + nz(pi.getCurrency())));
        }

        List<Shipment> shipments = shipmentMapper.selectList(
                Wrappers.<Shipment>lambdaQuery().eq(Shipment::getPiId, pi.getId())
                        .orderByAsc(Shipment::getShipDate).orderByAsc(Shipment::getId));
        for (Shipment s : shipments) {
            events.add(new TimelineEvent("SHIPMENT", "出货单", s.getShipmentNo(), dateStr(s.getShipDate()),
                    s.getStatus(), "出货数量 " + money(s.getQty())));
            if ("FULL_SHIPPED".equals(s.getStatus())) {
                events.add(new TimelineEvent("DELIVERY", "交货", s.getShipmentNo(), dateStr(s.getShipDate()),
                        "已交货", "客户交付完成"));
            }
        }
        boolean delivered = shipments.stream().anyMatch(s -> "FULL_SHIPPED".equals(s.getStatus()));
        if (delivered && purchaseOrders.stream().allMatch(this::isFullyPaidPurchaseOrder)) {
            events.add(new TimelineEvent("COMPLETE", "完成订单", pi.getPiNo(), null,
                    "已完成", "出货已交付且关联 PO 已全部付款"));
        }
        vo.setEvents(events);
        return vo;
    }

    private boolean isFullyPaidPurchaseOrder(PurchaseOrder po) {
        if (po == null || po.getTotalAmount() == null || po.getTotalAmount().signum() <= 0) {
            return false;
        }
        java.math.BigDecimal paid = financeMapper.selectList(Wrappers.<FinanceEntry>lambdaQuery()
                        .eq(FinanceEntry::getRelatedDocType, "PO")
                        .eq(FinanceEntry::getRelatedDocNo, po.getPoNo())
                        .eq(FinanceEntry::getDirection, "out"))
                .stream().map(FinanceEntry::getAmount).filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return paid.compareTo(po.getTotalAmount()) >= 0;
    }

    private List<PiTraceItem> traceItems(Long piId) {
        List<SalesPiItem> items = salesPiItemMapper.selectList(Wrappers.<SalesPiItem>lambdaQuery()
                .eq(SalesPiItem::getPiId, piId)
                .orderByAsc(SalesPiItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = items.stream()
                .map(SalesPiItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty()
                ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return items.stream().map(item -> toTraceItem(item, products.get(item.getProductId()))).collect(Collectors.toList());
    }

    private PiTraceItem toTraceItem(SalesPiItem item, Product product) {
        PiTraceItem vo = new PiTraceItem();
        vo.setProductId(item.getProductId());
        vo.setProdCode(firstNonBlank(item.getProdCode(), product != null ? product.getProdCode() : null));
        vo.setProductName(firstNonBlank(item.getProductName(),
                product != null ? product.getProductName() : null,
                product != null ? product.getProdNameCn() : null,
                product != null ? product.getProdNameEn() : null));
        vo.setProductModel(firstNonBlank(item.getProductModel(),
                product != null ? product.getCompanyModel() : null,
                product != null ? product.getSpec() : null));
        vo.setFactoryModel(firstNonBlank(item.getFactoryModel(), product != null ? product.getFactoryModel() : null));
        vo.setUnit(quantityUnit(firstNonBlank(item.getUnit(), product != null ? product.getUnit() : null)));
        vo.setQuoteId(item.getQuoteId());
        vo.setQty(item.getQty());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setRemark(item.getRemark());
        return vo;
    }

    private Customer loadCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerMapper.selectById(customerId);
    }

    private void assertVisible(SalesPi pi) {
        Long scopedOwnerId = dataScope.restrictOwnerId();
        if (scopedOwnerId == null || pi == null) {
            return;
        }
        if (!scopedOwnerId.equals(resolveOwnerId(pi))) {
            throw new BusinessException("无权查看该 PI 单据");
        }
    }

    private Long resolveOwnerId(SalesPi pi) {
        if (pi == null) {
            return null;
        }
        return pi.getOwnerId() != null ? pi.getOwnerId() : pi.getCreateBy();
    }

    private String ownerName(Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        return ownerNameMap(Set.of(ownerId)).getOrDefault(ownerId, "用户#" + ownerId);
    }

    private Map<Long, String> ownerNameMap(Set<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, this::userDisplay, (a, b) -> a));
    }

    private String userDisplay(SysUser user) {
        if (user == null) {
            return "";
        }
        return firstNonBlank(user.getNickname(), user.getRealName(), user.getUsername());
    }

    private String customerDisplay(Customer c) {
        if (c == null) {
            return null;
        }
        return firstNonBlank(c.getCompanyName(), c.getCustNameEn(), c.getCustNameCn(), c.getCustCode());
    }

    private String dateStr(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String money(java.math.BigDecimal v) {
        return v == null ? "0" : String.format("%,.2f", v);
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
}
