package com.CampusToursLive.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a real PostgreSQL (Testcontainers), fetches the
 * springdoc-generated OpenAPI spec (Contract B) from {@code /v3/api-docs}, asserts it is non-empty
 * and well-formed, and writes it to {@code target/openapi.json}. CI then lints that file with
 * Spectral (see {@code .spectral.yaml} + {@code .github/workflows/ci.yml}) so an under-documented
 * spec fails the build. The spec is generated at runtime from the controller/DTO annotations, so
 * there is no drift between code and spec — this is a completeness/quality gate.
 *
 * <p>The real {@link JwtDecoder} bean fetches Google's JWKS at startup; it is replaced with a mock
 * here so the context boots offline. The {@code /v3/api-docs} path is permitted unauthenticated in
 * {@code SecurityConfig}, so no token is needed to fetch the spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OpenApiDocsExportTest {

    static final Path OPENAPI_OUTPUT = Path.of("target", "openapi.json");

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private TestRestTemplate rest;

    @Test
    void exportsGeneratedOpenApiSpec() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody();
        assertThat(body).isNotBlank();
        // Sanity-check it is the OpenAPI document with documented paths + component schemas.
        assertThat(body).contains("\"openapi\"").contains("\"paths\"").contains("\"components\"");

        Files.createDirectories(OPENAPI_OUTPUT.getParent());
        Files.writeString(OPENAPI_OUTPUT, body);
        assertThat(Files.size(OPENAPI_OUTPUT)).isGreaterThan(0L);
    }

    /**
     * CTL-97: /userinfo and /session/current-role are removed (Core no longer owns current-role);
     * /users/me and /users/me/role-eligibility replace them. Asserted against the generated spec's
     * path keys rather than by firing an unauthenticated request — an unauthenticated request to a
     * removed, authenticated path 401s at the security chain before Spring Web would even 404 it,
     * so a request-based check can't distinguish "removed" from "still there but needs a token".
     */
    @Test
    void removedEndpoints_areAbsent_andReplacementsArePresent() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/v3/api-docs", String.class);
        String body = resp.getBody();

        assertThat(body).doesNotContain("\"/userinfo\"");
        assertThat(body).doesNotContain("\"/session/current-role\"");
        assertThat(body).contains("\"/users/me\"");
        assertThat(body).contains("\"/users/me/role-eligibility\"");
    }
}
