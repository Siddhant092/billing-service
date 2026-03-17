package com.broadnet.billing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;


//  OpenAPI 3 / Swagger UI configuration.
//  Access at: http://localhost:8080/swagger-ui.html

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI billingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Broadnet Billing Service API")
                        .description("REST API for billing, subscriptions, usage enforcement and Stripe integration.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local")))
                .tags(List.of(
                        new Tag().name("Dashboard").description("Billing snapshot, usage metrics, invoices"),
                        new Tag().name("Checkout").description("Stripe Checkout session creation"),
                        new Tag().name("Subscription").description("Plan changes, addons, cancel, reactivate"),
                        new Tag().name("Usage").description("Usage enforcement and analytics"),
                        new Tag().name("Plans").description("Plan and addon catalogue"),
                        new Tag().name("Admin - Billing").description("Admin: company billing operations"),
                        new Tag().name("Admin - Plans").description("Admin: plan/addon CRUD and Stripe sync"),
                        new Tag().name("Webhooks").description("Stripe webhook receiver")
                ));
    }
}