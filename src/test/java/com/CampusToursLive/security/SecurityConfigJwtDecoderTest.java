package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for {@link SecurityConfig#tokenValidator} — the audience fail-fast, and that a
 * configured audience is actually enforced. Exercised directly (no Spring context, no network: the
 * JWKS fetch lives in the {@code jwtDecoder} bean, not here).
 *
 * <p>There is deliberately no "start without an audience" case to test: the service has no opt-out.
 * A blank audience is a configuration error, not a mode.
 */
class SecurityConfigJwtDecoderTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String CLIENT_ID = "183708-this-app.apps.googleusercontent.com";

    /** A structurally-valid token (correct issuer, unexpired) whose audience is some OTHER app. */
    private static Jwt tokenForAnotherApp() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("sub")
                .issuer(ISSUER)
                .audience(List.of("some-other-app"))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private static Jwt tokenForThisApp() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("sub")
                .issuer(ISSUER)
                .audience(List.of(CLIENT_ID))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void blankAudience_failsFast() {
        // No opt-out exists. Booting without an audience would accept any validly-signed Google
        // id_token — i.e. anyone with a Google account — so it must not be reachable by omission.
        assertThatThrownBy(() -> SecurityConfig.tokenValidator(ISSUER, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.audience")
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void nonBlankAudience_buildsAudienceCheckingValidator() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.tokenValidator(ISSUER, CLIENT_ID);

        // Audience is enforced: another app's token is rejected, ours is accepted.
        assertThat(validator.validate(tokenForAnotherApp()).hasErrors()).isTrue();
        assertThat(validator.validate(tokenForThisApp()).hasErrors()).isFalse();
    }
}
