package com.CampusToursLive.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc/OpenAPI wiring. Declares the API metadata and a JWT bearer security scheme so the
 * generated spec documents auth and Swagger UI's "Authorize" button sends {@code Authorization:
 * Bearer <token>} (the same Google OIDC id_token authenticated requests carry -- the public
 * marketplace and health routes need none; see {@code SecurityConfig}).
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
                                        """
                        Contract B — the REST API the BFF consumes (bookings, guides, tours, \
                        availability, etc.).

                        ### Public routes

                        Most routes require the Bearer `id_token`, but the marketplace does not: \
                        `GET`/`HEAD` on `/tours`, `/tours/**` and `/meta/**` are served \
                        anonymously and never answer `401`.

                        ### Idempotent writes (`Idempotency-Key`)

                        Any `POST`/`PATCH`/`PUT`/`DELETE` may carry an `Idempotency-Key` header. \
                        It is opt-in: without it the request runs normally with no deduplication.

                        A repeat of the same key resolves to one of three outcomes, none of which \
                        appear in the per-operation responses below because they are produced by \
                        a servlet filter that runs BEFORE the exception handler this spec is \
                        generated from:

                        - **replay** — the first attempt finished; its stored status and body are \
                          returned again and the handler does not re-run.
                        - **`409`** — the first attempt is still in flight (a double-submit).
                        - **`422`** — same key, different request body: a client bug, not a retry.

                        The key is scoped per authenticated subject, so two users cannot collide \
                        on one key. A failed attempt (5xx or a thrown exception) deletes its own \
                        record so an honest retry is not wedged behind the `409`; anything left \
                        is reaped after 24 hours.
                        """)
                                .contact(
                                        new Contact()
                                                .name("CampusToursLive")
                                                .url("https://github.com/Campus-Tours-Live")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
