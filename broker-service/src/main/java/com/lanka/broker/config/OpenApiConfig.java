package com.lanka.broker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger metadata. UI: /swagger-ui.html on the service port. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI brokerServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lanka MicroJob - Broker Service API")
                        .version("1.0.0")
                        .description("Broker applications, admin approval, offline worker registration and "
                                + "commission tracking. Use the JWT from /brokers/login (BROKER) or "
                                + "/auth/admin/login (ADMIN)."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
