package com.erp.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 文档配置 - 注册 JWT Bearer 鉴权
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "Authorization";

    @Bean
    public OpenAPI erpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("外贸贸易管理 ERP 系统 API")
                        .version("1.0.0")
                        .description("覆盖客户、产品、销售、订单、出货、财务、报表的全流程 ERP")
                        .contact(new Contact().name("ERP Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
