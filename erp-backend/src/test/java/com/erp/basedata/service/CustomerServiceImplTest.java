package com.erp.basedata.service;

import com.erp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceImplTest {

    @Test
    void createsUniqueArchiveCodesForRepeatedImportsOfTheSameCustomerCode() {
        String first = CustomerServiceImpl.archivedCustomerCode("AR0001", 2077676586209456130L);
        String second = CustomerServiceImpl.archivedCustomerCode("AR0001", 2079176431024926722L);

        assertThat(first).isEqualTo("AR0001~D~2077676586209456130");
        assertThat(second).isEqualTo("AR0001~D~2079176431024926722");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void keepsArchivedCustomerCodeWithinDatabaseColumnLength() {
        String longCode = "客户编号".repeat(20);

        String archived = CustomerServiceImpl.archivedCustomerCode(longCode, Long.MAX_VALUE);

        assertThat(archived).hasSizeLessThanOrEqualTo(64);
        assertThat(archived).endsWith("~D~" + Long.MAX_VALUE);
    }

    @Test
    void acceptsImportedPhoneDetailsUpToDatabaseLimit() {
        CustomerServiceImpl.validatePhoneLength("+86 755 1234 5678 / WhatsApp +86 138 0000 0000");
    }

    @Test
    void reportsClearValidationMessageWhenImportedPhoneIsTooLong() {
        assertThatThrownBy(() -> CustomerServiceImpl.validatePhoneLength("1".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("联系电话不能超过 255 个字符");
    }
}
