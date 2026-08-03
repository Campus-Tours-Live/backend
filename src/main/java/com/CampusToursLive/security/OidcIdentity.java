package com.CampusToursLive.security;

import java.util.Objects;

/**
 * A caller's federated OIDC identity: the token issuer plus the {@code sub} claim.
 *
 * <p>This pair is the natural key available BEFORE a {@code users} row necessarily exists — at
 * first-time onboarding there is no row yet to lock by ID, only the identity the caller presented.
 * {@code issuer} namespaces the key so the same {@code subject} string minted by two different
 * identity providers can never collide (see {@link OidcIdentityLock}).
 */
public record OidcIdentity(String issuer, String subject) {

    public OidcIdentity {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
    }
}
