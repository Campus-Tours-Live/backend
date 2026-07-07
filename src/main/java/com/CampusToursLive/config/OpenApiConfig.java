package com.CampusToursLive.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc/OpenAPI wiring. Declares the API metadata and a JWT bearer security scheme so the
 * generated spec documents auth and Swagger UI's "Authorize" button sends {@code Authorization:
 * Bearer <token>} (the same Google OIDC id_token every non-doc request requires).
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    public OpenAPI coreApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CampusToursLive Core API")
                                .version("v1")
                                .description(
                                        "Contract B — the REST API the BFF consumes (bookings,"
                                                + " guides, tours, availability, etc.)."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
