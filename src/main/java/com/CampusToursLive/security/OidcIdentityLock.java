package com.CampusToursLive.security;

/**
 * Per-OIDC-identity serialization lock (CTL-97 Core-B Task 1).
 *
 * <p>At first-time onboarding no {@code users} row exists yet for a given {@link OidcIdentity}, so
 * there is nothing to acquire a row-level lock ON — a normal {@code SELECT ... FOR UPDATE} has
 * nothing to select. An advisory lock keyed by the identity itself serializes concurrent onboarding
 * attempts (and, later, other eligibility mutators) for the SAME identity, while two DIFFERENT
 * identities never block each other.
 */
public interface OidcIdentityLock {

    /**
     * Blocks until the transaction-scoped advisory lock for {@code id} is granted on the CURRENT
     * transaction. The lock releases automatically on commit or rollback of that transaction —
     * never held past it, never leaked if the caller throws — and re-acquiring the same identity
     * later in the same transaction is a harmless re-entrant no-op.
     *
     * @throws IllegalStateException if called without an active transaction on the current thread
     */
    void acquire(OidcIdentity id);
}
