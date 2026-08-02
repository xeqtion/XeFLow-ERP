package com.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataAccessGuard;
import com.erp.common.security.DataScopeHelper;
import com.erp.order.entity.OrderOem;
import com.erp.order.mapper.OrderOemMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.List;

@Service
public class OrderOemServiceImpl extends ServiceImpl<OrderOemMapper, OrderOem> implements OrderOemService {

    private final DataScopeHelper dataScope;
    private final CustomerMapper customerMapper;
    private final DataAccessGuard accessGuard;

    public OrderOemServiceImpl(DataScopeHelper dataScope, CustomerMapper customerMapper, DataAccessGuard accessGuard) {
        this.dataScope = dataScope;
        this.customerMapper = customerMapper;
        this.accessGuard = accessGuard;
    }

    @Override
    public boolean save(OrderOem entity) {
        accessGuard.assertPiAndCustomerVisible(entity.getPiId(), entity.getCustomerId());
        if (!accessGuard.viewAll()) {
            entity.setOwnerId(accessGuard.currentUserId());
        }
        fillCustomerSnapshot(entity);
        return super.save(entity);
    }

    @Override
    public IPage<OrderOem> pageQuery(PageQuery query) {
        return this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query.getKeyword()));
    }

    @Override
    public List<OrderOem> listByKeyword(String keyword) {
        return this.list(buildWrapper(keyword));
    }

    @Override
    public OrderOem getById(Serializable id) {
        OrderOem order = super.getById(id);
        assertVisible(order);
        return order;
    }

    @Override
    public boolean updateById(OrderOem entity) {
        if (entity != null) {
            OrderOem existing = super.getById(entity.getId());
            assertVisible(existing);
            accessGuard.assertPiAndCustomerVisible(entity.getPiId(), entity.getCustomerId());
            if (existing != null) {
                entity.setCreateBy(existing.getCreateBy());
                if (!accessGuard.viewAll() || entity.getOwnerId() == null) {
                    entity.setOwnerId(existing.getOwnerId());
                }
            }
            fillCustomerSnapshot(entity);
        }
        return super.updateById(entity);
    }

    @Override
    public boolean removeById(Serializable id) {
        assertVisible(super.getById(id));
        return super.removeById(id);
    }

    private LambdaQueryWrapper<OrderOem> buildWrapper(String keyword) {
        Long ownerId = dataScope.restrictOwnerId();
        LambdaQueryWrapper<OrderOem> wrapper = new LambdaQueryWrapper<OrderOem>()
                .eq(ownerId != null, OrderOem::getOwnerId, ownerId)
                .orderByDesc(OrderOem::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OrderOem::getOemNo, keyword)
                    .or().like(OrderOem::getPiNo, keyword)
                    .or().like(OrderOem::getCustomerCode, keyword)
                    .or().like(OrderOem::getCustomerName, keyword)
                    .or().like(OrderOem::getProductModel, keyword)
                    .or().like(OrderOem::getFactoryModel, keyword));
        }
        return wrapper;
    }

    private void assertVisible(OrderOem order) {
        Long ownerId = dataScope.restrictOwnerId();
        if (order == null || ownerId == null) {
            return;
        }
        if (!ownerId.equals(order.getOwnerId())) {
            throw new com.erp.common.exception.BusinessException("无权查看该 OEM 订单");
        }
    }

    private void fillCustomerSnapshot(OrderOem entity) {
        if (entity == null || entity.getCustomerId() == null) {
            return;
        }
        if (StringUtils.hasText(entity.getCustomerCode())) {
            return;
        }
        Customer customer = customerMapper.selectById(entity.getCustomerId());
        if (customer == null) {
            return;
        }
        entity.setCustomerCode(customer.getCustCode());
        if (!StringUtils.hasText(entity.getCustomerName())) {
            entity.setCustomerName(firstText(customer.getCompanyName(), customer.getCustNameEn(),
                    customer.getCustNameCn(), customer.getCustCode()));
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

}
