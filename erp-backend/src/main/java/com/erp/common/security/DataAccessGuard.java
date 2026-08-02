package com.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.exception.BusinessException;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Centralized row-level access checks for business data.
 */
@Component
public class DataAccessGuard {

    private final DataScopeHelper dataScope;
    private final CustomerMapper customerMapper;
    private final SalesPiMapper salesPiMapper;

    public DataAccessGuard(DataScopeHelper dataScope, CustomerMapper customerMapper, SalesPiMapper salesPiMapper) {
        this.dataScope = dataScope;
        this.customerMapper = customerMapper;
        this.salesPiMapper = salesPiMapper;
    }

    public boolean viewAll() {
        return dataScope.viewAll();
    }

    public Long restrictOwnerId() {
        return dataScope.restrictOwnerId();
    }

    public Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }

    public List<Long> visiblePiIdsForCurrentUser() {
        Long ownerId = restrictOwnerId();
        if (ownerId == null) {
            return Collections.emptyList();
        }
        return salesPiMapper.selectList(new LambdaQueryWrapper<SalesPi>()
                        .select(SalesPi::getId)
                        .eq(SalesPi::getOwnerId, ownerId))
                .stream()
                .map(SalesPi::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    public void assertOwnerVisible(Long ownerId, String resourceName) {
        Long current = restrictOwnerId();
        if (current == null || ownerId == null) {
            return;
        }
        if (!current.equals(ownerId)) {
            throw forbidden(resourceName);
        }
    }

    public void assertCreatorVisible(Long createBy, String resourceName) {
        Long current = restrictOwnerId();
        if (current == null || createBy == null) {
            return;
        }
        if (!current.equals(createBy)) {
            throw forbidden(resourceName);
        }
    }

    public void assertCustomerVisible(Long customerId) {
        if (customerId == null || viewAll()) {
            return;
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        Long current = restrictOwnerId();
        if (!"PERSONAL".equals(customer.getPoolType()) || !current.equals(customer.getOwnerId())) {
            throw forbidden("客户资料");
        }
    }

    public void assertPiVisible(Long piId) {
        SalesPi pi = getVisiblePi(piId);
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
    }

    public SalesPi getVisiblePi(Long piId) {
        if (piId == null) {
            return null;
        }
        SalesPi pi = salesPiMapper.selectById(piId);
        if (pi == null) {
            return null;
        }
        assertOwnerVisible(pi.getOwnerId(), "PI 单据");
        return pi;
    }

    public void assertPiAndCustomerVisible(Long piId, Long customerId) {
        SalesPi pi = getVisiblePi(piId);
        if (pi != null && customerId != null && pi.getCustomerId() != null && !customerId.equals(pi.getCustomerId())) {
            throw new BusinessException("客户与关联 PI 不一致");
        }
        if (pi == null) {
            assertCustomerVisible(customerId);
        }
    }

    public void assertRecordVisible(Long piId, Long ownerId, Long createBy, String resourceName) {
        if (viewAll()) {
            return;
        }
        if (piId != null) {
            assertPiVisible(piId);
            return;
        }
        if (ownerId != null) {
            assertOwnerVisible(ownerId, resourceName);
            return;
        }
        assertCreatorVisible(createBy, resourceName);
    }

    public boolean canSeeRecord(Long piId, Long ownerId, Long createBy) {
        if (viewAll()) {
            return true;
        }
        Long current = restrictOwnerId();
        if (current == null) {
            return true;
        }
        if (piId != null) {
            SalesPi pi = salesPiMapper.selectById(piId);
            return pi != null && current.equals(pi.getOwnerId());
        }
        if (ownerId != null) {
            return current.equals(ownerId);
        }
        return createBy == null || current.equals(createBy);
    }

    public boolean hasVisiblePiIds(Collection<Long> ids) {
        return ids != null && !ids.isEmpty();
    }

    private BusinessException forbidden(String resourceName) {
        return new BusinessException("无权访问该" + (resourceName == null ? "数据" : resourceName));
    }
}
