package com.erp.system.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSourceConfigurationTest {

    @Test
    void publicDeploymentPropertiesUseTheExpectedNamespace() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("erp.company.name"))
                .isEqualTo("${ERP_COMPANY_NAME:XeFlow Demo Company}");
        assertThat(properties.getProperty("erp.bootstrap-admin.enabled"))
                .isEqualTo("${ERP_BOOTSTRAP_ADMIN_ENABLED:false}");
        assertThat(properties).doesNotContainKeys(
                "spring.company.name",
                "spring.bootstrap-admin.enabled"
        );
    }
}
