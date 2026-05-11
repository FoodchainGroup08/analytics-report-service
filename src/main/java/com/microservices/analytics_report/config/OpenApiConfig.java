package com.microservices.analytics_report.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI analyticsReportOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FoodChain — Analytics & Report Service API")
                        .description("Order analytics and business reporting. " +
                                "Consumes Kafka order events to build per-branch daily summaries, " +
                                "and exposes endpoints to generate and retrieve performance reports.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FoodchainGroup08")
                                .email("team@foodchain.com")))
                .servers(List.of(
                        new Server()
                                .url("http://54.235.78.18:8080/api")
                                .description("Production (Live API Gateway)"),
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("API Gateway (local dev)"),
                        new Server()
                                .url("http://localhost:8085/api")
                                .description("Analytics & Report Service direct (local dev)")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token. Get it from POST /api/auth/login")));
    }
}
