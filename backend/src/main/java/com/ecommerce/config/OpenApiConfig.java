package com.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("大数据电商系统 API 文档")
                        .version("1.0.0")
                        .description("覆盖认证、商品、推荐、订单、优惠券、管理端等核心接口，用于联调、演示和答辩说明。")
                        .contact(new Contact()
                                .name("Ecommerce Recommendation Backend")
                                .email("support@example.com")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi authAndCatalogApi() {
        return GroupedOpenApi.builder()
                .group("01-auth-and-catalog")
                .pathsToMatch(
                        "/api/auth/**",
                        "/api/products/**",
                        "/api/search/**",
                        "/api/reviews/**")
                .build();
    }

    @Bean
    public GroupedOpenApi recommendationAndCouponApi() {
        return GroupedOpenApi.builder()
                .group("02-recommendation-and-coupon")
                .pathsToMatch(
                        "/api/recommendations/**",
                        "/api/coupons/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userTradeApi() {
        return GroupedOpenApi.builder()
                .group("03-user-trade")
                .pathsToMatch(
                        "/api/cart/**",
                        "/api/orders/**",
                        "/api/refunds/**",
                        "/api/address/**",
                        "/api/messages/**",
                        "/api/wallet/**",
                        "/api/user/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminAndMerchantApi() {
        return GroupedOpenApi.builder()
                .group("04-admin-and-merchant")
                .pathsToMatch(
                        "/api/admin/**",
                        "/api/merchant/**",
                        "/api/ai/**")
                .build();
    }
}
