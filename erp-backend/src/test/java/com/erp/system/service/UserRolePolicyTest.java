package com.erp.system.service;

import com.erp.common.exception.BusinessException;
import com.erp.system.entity.SysRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRolePolicyTest {

    @Test
    void keepsOnlySuperAdminForBuiltInAdminUser() {
        assertThat(UserRolePolicy.normalize(1L, List.of(role(4L, "salesman"))))
                .containsExactly(0L);
    }

    @Test
    void rejectsSuperAdminForAnyOtherUser() {
        assertThatThrownBy(() -> UserRolePolicy.normalize(2L, List.of(role(0L, "super_admin"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅限");
    }

    @Test
    void administratorRoleOverridesAllOtherRoles() {
        assertThat(UserRolePolicy.normalize(2L, List.of(
                role(4L, "salesman"), role(1L, "admin"), role(2L, "finance"))))
                .containsExactly(1L);
    }

    @Test
    void readonlyCannotCoexistWithOtherRoles() {
        assertThatThrownBy(() -> UserRolePolicy.normalize(2L, List.of(
                role(5L, "readonly"), role(4L, "salesman"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只读权限");
    }

    @Test
    void limitsOrdinaryRoleCombinationToThree() {
        assertThatThrownBy(() -> UserRolePolicy.normalize(2L, List.of(
                role(2L, "finance"), role(3L, "sales_manager"),
                role(4L, "salesman"), role(6L, "custom"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多");

        assertThat(UserRolePolicy.normalize(2L, List.of(
                role(2L, "finance"), role(3L, "sales_manager"), role(4L, "salesman"))))
                .containsExactly(2L, 3L, 4L);
    }

    private SysRole role(Long id, String key) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey(key);
        role.setStatus(1);
        return role;
    }
}
