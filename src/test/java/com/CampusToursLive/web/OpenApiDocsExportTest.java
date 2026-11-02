package com.CampusToursLive.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired private ObjectMapper mapper;

    @Test
    void guidePendingActions_hasAnEnvelopedSuccessExample() throws Exception {
        String body = rest.getForObject("/v3/api-docs", String.class);
        JsonNode content =
                mapper.readTree(body)
                        .path("paths")
                        .path("/guide/bookings/pending-actions")
                        .path("get")
                        .path("responses")
                        .path("200")
                        .path("content")
                        .path("application/json");
        JsonNode example = content.path("example");
        if (example.isMissingNode()) {
            assertThat(content.path("examples").isEmpty()).isFalse();
            example = content.path("examples").elements().next().path("value");
        }
        assertThat(example.path("data").path("pendingAcceptance").asInt(-1)).isEqualTo(2);
        assertThat(example.path("meta").path("requestId").asText()).isNotBlank();
        JsonNode schemas = mapper.readTree(body).path("components").path("schemas");
        assertThat(content.path("schema").path("$ref").asText())
                .endsWith("/GuidePendingActionsEnvelope");
        assertThat(
                        schemas.path("GuidePendingActionsEnvelope")
                                .path("properties")
                                .path("data")
                                .path("$ref")
                                .asText())
                .endsWith("/GuidePendingActionsResponse");
        assertThat(
                        schemas.path("GuidePendingActionsResponse")
                                .path("properties")
                                .path("pendingAcceptance")
                                .path("type")
                                .asText())
                .isEqualTo("integer");
    }

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

    @Test
    void guideActionExamplesMatchSuccessfulTransition() throws Exception {
        var spec =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(rest.getForObject("/v3/api-docs", String.class));
        for (var entry :
                java.util.Map.of(
                                "accept",
                                "CONFIRMED",
                                "decline",
                                "CANCELLED",
                                "complete",
                                "COMPLETED",
                                "no-show",
                                "PARTICIPANT_NO_SHOW")
                        .entrySet()) {
            var example =
                    spec.path("paths")
                            .path("/guide/bookings/{id}/" + entry.getKey())
                            .path("post")
                            .path("responses")
                            .path("200")
                            .path("content")
                            .path("application/json")
                            .path("example");
            assertThat(example.path("data").path("status").asText()).isEqualTo(entry.getValue());
            assertThat(example.path("data").path("bookingNumber").asText()).isNotBlank();
            assertThat(example.has("meta")).isTrue();
            if (!entry.getKey().equals("decline")) {
                assertThat(example.path("data").path("guideResponseDeadline").isNull()).isTrue();
            } else {
                assertThat(example.path("data").path("guideResponseDeadline").asText())
                        .isEqualTo("2026-07-30T15:00:00Z");
            }
        }
    }

    /**
     * CTL-97: /userinfo and /session/current-role are removed (Core no longer owns current-role);
     * /users/me and /users/me/role-eligibility replace them. Asserted against the generated spec's
     * path keys rather than by firing an unauthenticated request — an unauthenticated request to a
     * removed, authenticated path 401s at the security chain before Spring Web would even 404 it,
     * so a request-based check can't distinguish "removed" from "still there but needs a token".
     *
     * <p>The old OAuth-time JIT-provisioning session endpoint was also removed in CTL-97 Task 11 —
     * provisioning now happens ONLY at onboarding submit ({@code UserProvisioningService
     * .createUserForOnboarding}, called from {@code OnboardingService}). The whole {@code
     * "/session"} path key is asserted absent below, since removing the only mapping on it means
     * springdoc no longer emits the path at all (not even for GET).
     */
    @Test
    void removedEndpoints_areAbsent_andReplacementsArePresent() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/v3/api-docs", String.class);
        String body = resp.getBody();

        assertThat(body).doesNotContain("\"/userinfo\"");
        assertThat(body).doesNotContain("\"/session/current-role\"");
        assertThat(body).doesNotContain("\"/session\"");
        assertThat(body).contains("\"/users/me\"");
        assertThat(body).contains("\"/users/me/role-eligibility\"");
    }
}
