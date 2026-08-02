package com.erp.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataAccessGuard;
import com.erp.common.security.DataScopeHelper;
import com.erp.common.security.PermissionService;
import com.erp.common.security.SecurityUtils;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.service.NotificationService;
import com.erp.sales.dto.ContractPriceQuery;
import com.erp.sales.entity.ContractPrice;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.mapper.ContractPriceMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售报价服务 - 按产品报价区间自动判定审核状态，并限制提交人与审核人的操作边界。
 */
@Service
public class ContractPriceServiceImpl extends ServiceImpl<ContractPriceMapper, ContractPrice>
        implements ContractPriceService {

    private static final String AUDIT_PERMISSION = "contract_price:approve";
    private static final List<String> AUDIT_NOTIFY_ROLES = List.of("sales_manager", "admin", "super_admin");
    private static final Duration REJECT_EDIT_WINDOW = Duration.ofHours(24);
    private static final String AUDIT_NOTICE_TITLE = "销售报价审核结果通知";
    private static final String AUDIT_REQUEST_NOTICE_TITLE = "销售报价待审核";

    private final StandardPriceService standardPriceService;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final DataScopeHelper dataScope;
    private final DataAccessGuard accessGuard;

    public ContractPriceServiceImpl(StandardPriceService standardPriceService,
                                    ProductMapper productMapper,
                                    CustomerMapper customerMapper,
                                    SysUserMapper userMapper,
                                    PermissionService permissionService,
                                    NotificationService notificationService,
                                    DataScopeHelper dataScope,
                                    DataAccessGuard accessGuard) {
        this.standardPriceService = standardPriceService;
        this.productMapper = productMapper;
        this.customerMapper = customerMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
        this.dataScope = dataScope;
        this.accessGuard = accessGuard;
    }

    @Override
    public IPage<ContractPrice> pageQuery(ContractPriceQuery query) {
        cleanupExpiredRejected();
        Long ownerId = dataScope.restrictOwnerId();
        Long customerId = resolveCustomerId(query.getCustomerCode(), query.getCustomerId());
        LambdaQueryWrapper<ContractPrice> wrapper = new LambdaQueryWrapper<ContractPrice>()
                .eq(ownerId != null, ContractPrice::getCreateBy, ownerId)
                .eq(customerId != null, ContractPrice::getCustomerId, customerId)
                .eq(StringUtils.hasText(query.getAuditStatus()), ContractPrice::getAuditStatus, query.getAuditStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> {
                    String kw = query.getKeyword().trim();
                    List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                                    .select(Customer::getId)
                                    .like(Customer::getCustCode, kw))
                            .stream().map(Customer::getId).toList();
                    w.like(ContractPrice::getProductName, kw)
                            .or().like(ContractPrice::getProductModel, kw)
                            .or().like(ContractPrice::getCustomerName, kw)
                            .or().like(ContractPrice::getProdCode, kw)
                            .or().like(ContractPrice::getRemark, kw);
                    if (!customerIds.isEmpty()) {
                        w.or().in(ContractPrice::getCustomerId, customerIds);
                    }
                })
                .orderByDesc(ContractPrice::getCreateTime);
        IPage<ContractPrice> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        hydrateCustomerCodes(page.getRecords());
        return page;
    }

    @Override
    public ContractPrice getVisible(Long id) {
        cleanupExpiredRejected();
        ContractPrice cp = getExisting(id);
        assertVisible(cp);
        hydrateCustomerCode(cp);
        return cp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWithCompare(ContractPrice cp) {
        cp.setId(null);
        cp.setCreateBy(currentUserId());
        accessGuard.assertCustomerVisible(cp.getCustomerId());
        prepare(cp);
        evaluate(cp);
        stampAutomaticTerminalStatus(cp);
        this.save(cp);
        notifyAutomaticResult(cp);
        notifyPendingAudit(cp);
        return cp.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithCompare(ContractPrice cp) {
        if (cp.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        ContractPrice existing = getExisting(cp.getId());
        assertCanEdit(existing);
        accessGuard.assertCustomerVisible(cp.getCustomerId());
        cp.setCreateBy(existing.getCreateBy());

        prepare(cp);
        cp.setAuditorId(null);
        cp.setAuditTime(null);
        evaluate(cp);
        stampAutomaticTerminalStatus(cp);
        this.updateById(cp);
        notifyAutomaticResult(cp);
        notifyPendingAudit(cp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ContractPrice cp = getExisting(id);
        assertCanCancel(cp);
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        ContractPrice cp = getExisting(id);
        if (isBelowFloor(cp)) {
            throw new BusinessException("成交单价低于底价, 不能审批通过");
        }
        cp.setAuditStatus(PASS);
        cp.setAuditorId(SecurityUtils.getCurrentUserId());
        cp.setAuditTime(LocalDateTime.now());
        this.updateById(cp);
        notifyAuditResult(cp, "已通过", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        ContractPrice cp = getExisting(id);
        cp.setAuditStatus(REJECTED);
        cp.setAuditorId(SecurityUtils.getCurrentUserId());
        cp.setAuditTime(LocalDateTime.now());
        this.updateById(cp);
        notifyAuditResult(cp, "未通过", reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestReview(Long id) {
        ContractPrice cp = getExisting(id);
        if (isBelowFloor(cp)) {
            throw new BusinessException("成交单价低于底价, 不能重新审核");
        }
        cp.setAuditStatus(PENDING);
        cp.setAuditorId(SecurityUtils.getCurrentUserId());
        cp.setAuditTime(LocalDateTime.now());
        this.updateById(cp);
        notifyReviewRequested(cp);
        notifyPendingAudit(cp);
    }

    @Scheduled(fixedDelayString = "${erp.sales-quote.rejected-cleanup-delay-ms:600000}",
            initialDelayString = "${erp.sales-quote.rejected-cleanup-initial-delay-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredRejected() {
        LocalDateTime expireBefore = LocalDateTime.now().minus(REJECT_EDIT_WINDOW);
        this.remove(new LambdaUpdateWrapper<ContractPrice>()
                .eq(ContractPrice::getAuditStatus, REJECTED)
                .isNotNull(ContractPrice::getAuditTime)
                .lt(ContractPrice::getAuditTime, expireBefore));
    }

    private void prepare(ContractPrice cp) {
        validate(cp);

        Product product = productMapper.selectById(cp.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: " + cp.getProductId());
        }
        Customer customer = customerMapper.selectById(cp.getCustomerId());
        if (customer == null) {
            throw new BusinessException("客户不存在: " + cp.getCustomerId());
        }

        cp.setProductName(StringUtils.hasText(cp.getProductName())
                ? cp.getProductName().trim()
                : firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn()));
        cp.setProductModel(StringUtils.hasText(cp.getProductModel())
                ? cp.getProductModel().trim()
                : firstText(product.getCompanyModel(), product.getProdCode(), product.getSpec()));
        cp.setProdCode(StringUtils.hasText(product.getProdCode()) ? product.getProdCode() : cp.getProdCode());
        fillCustomerSnapshot(cp, customer);
        if (StringUtils.hasText(cp.getCurrency())) {
            cp.setCurrency(cp.getCurrency().trim().toUpperCase());
        } else {
            cp.setCurrency("USD");
        }
    }

    /**
     * 核心规则:
     * 低于底价 -> 强制驳回; 低于折扣单价或高于标准单价 -> 待审核; 位于折扣单价和标准单价之间 -> 通过。
     * 偏差率按越界边界计算: 低于下边界为负, 高于上边界为正。
     */
    private void evaluate(ContractPrice cp) {
        StandardPrice sp = standardPriceService.findApplicable(cp.getProductId(), cp.getCustomerId());
        BigDecimal standard = sp != null ? sp.getStandardUnitPrice() : null;
        BigDecimal discount = sp != null ? sp.getDiscountUnitPrice() : null;
        BigDecimal floor = sp != null ? sp.getFloorPrice() : null;
        cp.setStandardPrice(standard);
        if (StringUtils.hasText(cp.getCurrency())) {
            cp.setCurrency(cp.getCurrency().trim().toUpperCase());
        } else if (sp != null && StringUtils.hasText(sp.getCurrency())) {
            cp.setCurrency(sp.getCurrency().trim().toUpperCase());
        } else {
            cp.setCurrency("USD");
        }

        if (standard == null || discount == null || standard.signum() == 0) {
            cp.setDeviation(null);
            cp.setAuditStatus(PENDING);
            return;
        }

        BigDecimal deal = cp.getContractPrice();
        BigDecimal deviation = rate(deal, standard);
        cp.setDeviation(deviation);
        BigDecimal lower = standard.min(discount);
        BigDecimal upper = standard.max(discount);
        if (deviation != null && deviation.compareTo(BigDecimal.ZERO) == 0) {
            cp.setAuditStatus(PASS);
        } else if (floor != null && deal.compareTo(floor) < 0) {
            cp.setAuditStatus(REJECTED);
        } else if (deal.compareTo(lower) < 0) {
            cp.setAuditStatus(PENDING);
        } else if (deal.compareTo(upper) > 0) {
            cp.setAuditStatus(PENDING);
        } else {
            cp.setAuditStatus(PASS);
        }
    }

    private BigDecimal rate(BigDecimal deal, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return deal.subtract(base).divide(base, 6, RoundingMode.HALF_UP);
    }

    private void stampAutomaticTerminalStatus(ContractPrice cp) {
        if (PASS.equals(cp.getAuditStatus()) || REJECTED.equals(cp.getAuditStatus())) {
            cp.setAuditTime(LocalDateTime.now());
        }
    }

    private void notifyAutomaticResult(ContractPrice cp) {
        if (PASS.equals(cp.getAuditStatus())) {
            notifyAuditResult(cp, "已通过", null);
        } else if (REJECTED.equals(cp.getAuditStatus())) {
            notifyAuditResult(cp, "未通过", "成交单价低于底价，系统已强制驳回。");
        }
    }

    private void notifyAuditResult(ContractPrice cp, String result, String reason) {
        if (cp.getCreateBy() == null) {
            return;
        }
        StringBuilder content = new StringBuilder()
                .append("您提交的销售报价")
                .append(quoteSummary(cp))
                .append("审核").append(result).append("。");
        if (REJECTED.equals(cp.getAuditStatus())) {
            if (StringUtils.hasText(reason)) {
                content.append("驳回原因：").append(normalizeReason(reason)).append("。");
            }
            content.append("请在24小时内完成修改并重新提交审核，逾期系统将自动撤销该报价。");
        } else if (PASS.equals(cp.getAuditStatus())) {
            content.append("请根据业务流程继续跟进。");
        }
        notificationService.createForUser(cp.getCreateBy(), AUDIT_NOTICE_TITLE, content.toString(), NotificationType.SYSTEM);
    }

    private void notifyReviewRequested(ContractPrice cp) {
        if (cp.getCreateBy() == null) {
            return;
        }
        String content = "您提交的销售报价" + quoteSummary(cp) + "已重新进入审核流程。请等待审核结果通知。";
        notificationService.createForUser(cp.getCreateBy(), "销售报价重新审核通知", content, NotificationType.SYSTEM);
    }

    private void notifyPendingAudit(ContractPrice cp) {
        if (!PENDING.equals(cp.getAuditStatus())) {
            return;
        }
        String applicant = applicantName(cp.getCreateBy());
        String content = applicant + "申请的销售报价（报价编号：" + cp.getId() + "）正在等待销售主管的审核。";
        notificationService.createForRoles(AUDIT_NOTIFY_ROLES, AUDIT_REQUEST_NOTICE_TITLE, content,
                NotificationType.SYSTEM, "contract-price-audit-" + cp.getId());
    }

    private String applicantName(Long userId) {
        if (userId == null) {
            return "业务员";
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return "业务员";
        }
        return firstText(user.getNickname(), user.getRealName(), user.getUsername(), "业务员");
    }

    private String quoteSummary(ContractPrice cp) {
        return "（报价编号：" + cp.getId()
                + "，客户：" + textOrDash(cp.getCustomerName())
                + "，产品：" + textOrDash(cp.getProductName())
                + "，产品型号：" + textOrDash(cp.getProductModel())
                + "，成交单价：" + cp.getContractPrice() + " " + textOrDash(cp.getCurrency())
                + "）";
    }

    private String textOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String normalizeReason(String reason) {
        String cleaned = reason == null ? "" : reason.trim();
        while (cleaned.endsWith("。") || cleaned.endsWith(".")
                || cleaned.endsWith("；") || cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private void hydrateCustomerCodes(List<ContractPrice> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream()
                .map(ContractPrice::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Customer> customers = customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a));
        records.forEach(cp -> {
            Customer customer = customers.get(cp.getCustomerId());
            if (customer != null) {
                fillCustomerSnapshot(cp, customer);
            }
        });
    }

    private void hydrateCustomerCode(ContractPrice cp) {
        if (cp == null || cp.getCustomerId() == null) {
            return;
        }
        Customer customer = customerMapper.selectById(cp.getCustomerId());
        if (customer != null) {
            fillCustomerSnapshot(cp, customer);
        }
    }

    private void fillCustomerSnapshot(ContractPrice cp, Customer customer) {
        cp.setCustomerCode(customer.getCustCode());
        cp.setCustomerName(StringUtils.hasText(cp.getCustomerName())
                ? cp.getCustomerName().trim()
                : firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(), customer.getCustCode()));
    }

    private void assertCanEdit(ContractPrice existing) {
        if (!hasAuditPermission() && !isOwner(existing)) {
            throw new BusinessException("无权修改该销售报价");
        }
        if (PASS.equals(existing.getAuditStatus())) {
            String action = hasAuditPermission() ? "删除" : "撤销";
            throw new BusinessException("已通过的销售报价不能更改数据, 如需" + action + "请执行" + action + "操作");
        }
        if (REJECTED.equals(existing.getAuditStatus()) && isRejectedExpired(existing)) {
            this.removeById(existing.getId());
            throw new BusinessException("该销售报价被驳回已超过24小时, 系统已自动撤销");
        }
        if (!PENDING.equals(existing.getAuditStatus()) && !REJECTED.equals(existing.getAuditStatus())) {
            throw new BusinessException("当前状态不允许修改销售报价");
        }
    }

    private void assertCanCancel(ContractPrice cp) {
        if (!hasAuditPermission() && !isOwner(cp)) {
            throw new BusinessException("无权撤销该销售报价");
        }
    }

    private boolean isRejectedExpired(ContractPrice cp) {
        return REJECTED.equals(cp.getAuditStatus())
                && cp.getAuditTime() != null
                && cp.getAuditTime().plus(REJECT_EDIT_WINDOW).isBefore(LocalDateTime.now());
    }

    private void validate(ContractPrice cp) {
        if (cp.getCustomerId() == null || cp.getProductId() == null) {
            throw new BusinessException("客户和产品不能为空");
        }
        if (cp.getContractPrice() == null) {
            throw new BusinessException("成交单价不能为空");
        }
    }

    private boolean isBelowFloor(ContractPrice cp) {
        StandardPrice sp = standardPriceService.findApplicable(cp.getProductId(), cp.getCustomerId());
        BigDecimal floor = sp != null ? sp.getFloorPrice() : null;
        return floor != null && cp.getContractPrice() != null && cp.getContractPrice().compareTo(floor) < 0;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Long resolveCustomerId(String customerCode, Long fallbackId) {
        if (!StringUtils.hasText(customerCode)) {
            return fallbackId;
        }
        Long id = customerMapper.selectIdByCode(customerCode.trim());
        return id != null ? id : -1L;
    }

    private boolean hasAuditPermission() {
        return permissionService.has(AUDIT_PERMISSION);
    }

    private boolean isOwner(ContractPrice cp) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId != null && cp.getCreateBy() != null && userId.equals(cp.getCreateBy());
    }

    private void assertVisible(ContractPrice cp) {
        if (!dataScope.viewAll() && !isOwner(cp)) {
            throw new BusinessException("无权查看该销售报价");
        }
    }

    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }

    private ContractPrice getExisting(Long id) {
        ContractPrice cp = this.getById(id);
        if (cp == null) {
            throw new BusinessException("销售报价记录不存在");
        }
        if (isRejectedExpired(cp)) {
            this.removeById(id);
            throw new BusinessException("该销售报价被驳回已超过24小时, 系统已自动撤销");
        }
        return cp;
    }
}
