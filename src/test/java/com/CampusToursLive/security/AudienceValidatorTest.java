package com.CampusToursLive.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * AudienceValidator — the guard that stops a validly-signed Google id_token minted for SOME OTHER
 * app from being accepted here. Acceptance requires our Client ID to be present in the token's
 * "aud" claim.
 */
class AudienceValidatorTest {

    private static final String CLIENT_ID = "183708-this-app.apps.googleusercontent.com";

    /** Build a Jwt whose "aud" claim is {@code aud} (or absent when null). */
    private static Jwt jwt(List<String> aud) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub");
        if (aud != null) {
            b.claim("aud", aud);
        }
        return b.build();
    }

    @Test
    void succeeds_whenAudienceContainsOurClientId() {
        var result = new AudienceValidator(CLIENT_ID).validate(jwt(List.of(CLIENT_ID, "extra")));
        assertFalse(result.hasErrors());
    }

    @Test
    void fails_whenAudienceIsForAnotherApp() {
        var result = new AudienceValidator(CLIENT_ID).validate(jwt(List.of("some-other-app")));
        assertTrue(result.hasErrors());
    }

    @Test
    void fails_whenTokenHasNoAudienceClaim() {
        var result = new AudienceValidator(CLIENT_ID).validate(jwt(null));
        assertTrue(result.hasErrors());
    }
}
