package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcIdentityTest {

    @Test
    void constructor_rejectsNullIssuer() {
        assertThatThrownBy(() -> new OidcIdentity(null, "sub-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_rejectsNullSubject() {
        assertThatThrownBy(() -> new OidcIdentity("https://accounts.google.com", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        OidcIdentity a = new OidcIdentity("https://accounts.google.com", "sub-1");
        OidcIdentity b = new OidcIdentity("https://accounts.google.com", "sub-1");
        OidcIdentity differentSubject = new OidcIdentity("https://accounts.google.com", "sub-2");
        OidcIdentity differentIssuer = new OidcIdentity("https://appleid.apple.com", "sub-1");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(differentSubject);
        assertThat(a).isNotEqualTo(differentIssuer);
        assertThat(a.issuer()).isEqualTo("https://accounts.google.com");
        assertThat(a.subject()).isEqualTo("sub-1");
    }
}
