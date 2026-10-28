package com.erp.common.config;

import com.erp.common.api.Result;
import com.erp.common.page.PageResult;
import com.erp.report.dto.SalesmanRow;
import com.erp.shipment.entity.Shipment;
import com.erp.system.controller.SysRoleController;
import com.erp.system.dto.SysUserVO;
import com.erp.system.service.SysRoleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@JsonTest
@ContextConfiguration(classes = IdentifierJsonConfig.class)
class IdentifierJsonConfigTest {
    private static final long ID = 2112086874144161794L;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void allNoArgDomainEntitiesAndDtosSerializeIdentifierFieldsLosslessly() throws Exception {
        int checkedProperties = 0;
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:com/erp/**/*.class")) {
            String url = resource.getURL().toString();
            String name = url.substring(url.indexOf("com/erp/"), url.length() - ".class".length()).replace('/', '.');
            if (!(name.contains(".entity.") || name.contains(".dto.")
                    || name.equals("com.erp.common.audit.AuditLog") || name.equals("com.erp.fileshare.SharedFile"))) {
                continue;
            }
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.isEnum()) continue;
            Object bean;
            try {
                var constructor = type.getDeclaredConstructor();
                if (!Modifier.isPublic(constructor.getModifiers())) continue; // Lombok builders are not API payloads.
                bean = constructor.newInstance();
            } catch (NoSuchMethodException ignored) {
                continue; // Immutable constructor-only DTOs have their own explicit serializers.
            }
            for (Class<?> parent = type; parent != Object.class; parent = parent.getSuperclass()) {
                for (Field field : parent.getDeclaredFields()) {
                    String key = field.getName();
                    if (Modifier.isStatic(field.getModifiers())
                            || (field.getType() != Long.class && field.getType() != long.class)
                            || !(key.equals("id") || key.endsWith("Id") || key.equals("createBy") || key.equals("updateBy"))) continue;
                    field.setAccessible(true);
                    field.set(bean, ID);
                    JsonNode node = mapper.valueToTree(bean);
                    assertThat(node.has(key)).as(type.getSimpleName() + "." + key).isTrue();
                    assertThat(node.get(key).isTextual()).as(type.getSimpleName() + "." + key).isTrue();
                    assertThat(node.get(key).textValue()).isEqualTo(Long.toString(ID));
                    checkedProperties++;
                }
            }
        }
        assertThat(checkedProperties).isGreaterThan(90);
    }

    @Test
    void shipmentReferencesSurviveStringJsonRoundTripAndKeepNumbersNumeric() throws Exception {
        Shipment shipment = new Shipment();
        shipment.setId(ID);
        shipment.setPiId(ID + 1);
        shipment.setSiId(ID + 2);
        shipment.setQty(new BigDecimal("10.1250"));
        shipment.setPackageCount(2);
        String json = mapper.writeValueAsString(shipment);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("piId").textValue()).isEqualTo(Long.toString(ID + 1));
        assertThat(tree.get("siId").textValue()).isEqualTo(Long.toString(ID + 2));
        assertThat(tree.get("qty").isNumber()).isTrue();
        assertThat(tree.get("packageCount").isIntegralNumber()).isTrue();
        Shipment restored = mapper.readValue(json, Shipment.class);
        assertThat(restored.getPiId()).isEqualTo(ID + 1);
        assertThat(restored.getSiId()).isEqualTo(ID + 2);
    }

    @Test
    void identifierCollectionsAndScalarCreateResponsesAreStrings() throws Exception {
        SysUserVO user = new SysUserVO();
        user.setRoleIds(Arrays.asList(0L, ID, ID + 1, null));
        JsonNode roles = mapper.valueToTree(user).get("roleIds");
        assertThat(roles.get(0).textValue()).isEqualTo("0");
        assertThat(roles.get(1).textValue()).isEqualTo(Long.toString(ID));
        assertThat(roles.get(2).textValue()).isEqualTo(Long.toString(ID + 1));
        assertThat(roles.get(3).isNull()).isTrue();
        assertThat(mapper.valueToTree(Result.id(ID)).get("data").textValue()).isEqualTo(Long.toString(ID));
        assertThat(mapper.valueToTree(Result.id("created", ID)).get("message").textValue()).isEqualTo("created");

        SysRoleService service = mock(SysRoleService.class);
        when(service.permissionIds(1L)).thenReturn(List.of(ID, ID + 1));
        JsonNode permissions = mapper.valueToTree(new SysRoleController(service).permissions(1L)).get("data");
        assertThat(permissions.get(1).textValue()).isEqualTo(Long.toString(ID + 1));
    }

    @Test
    void paginationStatisticsAndOtherLongValuesRemainNumbers() {
        PageResult<Object> page = new PageResult<>();
        page.setTotal(123L);
        page.setPageNum(1L);
        page.setPageSize(10L);
        JsonNode paging = mapper.valueToTree(page);
        assertThat(paging.get("total").isIntegralNumber()).isTrue();
        assertThat(paging.get("pageNum").isIntegralNumber()).isTrue();
        SalesmanRow row = new SalesmanRow();
        row.setOwnerId(ID);
        row.setOrderCount(8L);
        row.setTotalAmount(new BigDecimal("12.34"));
        JsonNode stats = mapper.valueToTree(row);
        assertThat(stats.get("ownerId").isTextual()).isTrue();
        assertThat(stats.get("orderCount").isIntegralNumber()).isTrue();
        assertThat(stats.get("totalAmount").isNumber()).isTrue();
        JsonNode count = mapper.valueToTree(Result.success(Map.of("count", 42L)));
        assertThat(count.get("data").get("count").isIntegralNumber()).isTrue();
    }
}
