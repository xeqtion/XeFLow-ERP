package com.erp.basedata.service;

import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerExtraFieldMapper;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataScopeHelper;
import com.erp.common.security.LoginUser;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.service.NotificationService;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPoolTransferServiceTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerExtraFieldMapper extraFieldMapper;
    @Mock
    private DataScopeHelper dataScope;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private NotificationService notificationService;

    private CustomerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(extraFieldMapper, dataScope, userMapper, notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", customerMapper);
        login(1L, "manager", Set.of("customer:update", "data:all"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void transfersPersonalCustomerAndNotifiesPreviousOwnerWithReason() {
        Customer customer = personalCustomer();
        SysUser manager = user(1L, "销售主管 Mary", "manager");
        when(dataScope.viewAll()).thenReturn(true);
        when(userMapper.selectRoleKeysByUserId(1L)).thenReturn(List.of("sales_manager"));
        when(userMapper.selectById(1L)).thenReturn(manager);
        when(customerMapper.selectById(10L)).thenReturn(customer);
        when(customerMapper.moveToPublicPool(10L, 20L, 1L)).thenReturn(1);

        service.transferToPublicPool(10L, "调整区域跟进安排");

        verify(customerMapper).moveToPublicPool(10L, 20L, 1L);
        verify(notificationService).createForUser(
                eq(20L),
                eq("个人客户转入公共客户池通知"),
                contains("销售主管 Mary转移至公共发展客户池。转移理由：调整区域跟进安排"),
                eq(NotificationType.SYSTEM));
    }

    @Test
    void rejectsTransferForUserWithoutCustomerPoolManagementScope() {
        when(dataScope.viewAll()).thenReturn(false);

        assertThatThrownBy(() -> service.transferToPublicPool(10L, "测试"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅销售主管或管理员可以管理客户池");

        verify(customerMapper, never()).selectById(10L);
        verify(notificationService, never()).createForUser(any(), any(), any(), any());
    }

    @Test
    void automaticRecycleAlsoNotifiesPreviousOwner() {
        Customer customer = personalCustomer();
        when(customerMapper.selectInactivePersonalCustomers()).thenReturn(List.of(customer));
        when(customerMapper.moveToPublicPool(10L, 20L, null)).thenReturn(1);

        service.recycleInactivePersonalCustomers();

        verify(customerMapper).moveToPublicPool(10L, 20L, null);
        verify(notificationService).createForUser(
                eq(20L),
                eq("个人客户转入公共客户池通知"),
                contains("系统转移至公共发展客户池"),
                eq(NotificationType.SYSTEM));
    }

    private Customer personalCustomer() {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setOwnerId(20L);
        customer.setPoolType("PERSONAL");
        customer.setOwnerAssignedAt(LocalDateTime.now().minusMonths(7));
        customer.setCustCode("US9101");
        customer.setCompanyName("Alice Lighting LLC");
        return customer;
    }

    private SysUser user(Long id, String realName, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        user.setStatus(1);
        return user;
    }

    private void login(Long id, String username, Set<String> permissions) {
        SysUser user = user(id, username, username);
        user.setPassword("encoded");
        LoginUser loginUser = new LoginUser(user, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
