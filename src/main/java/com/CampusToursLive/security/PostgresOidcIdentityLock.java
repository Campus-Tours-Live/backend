package com.CampusToursLive.security;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PostgreSQL {@link OidcIdentityLock}: mirrors {@code
 * com.CampusToursLive.domain.availability.GuideAdvisoryLock}'s pattern exactly — {@code
 * pg_advisory_xact_lock(hashtextextended(key::text, 0))} on the current transaction's {@link
 * EntityManager}. {@code hashtextextended} is PostgreSQL's stable, cross-process 64-bit hash (NOT
 * {@link String#hashCode()}, which is JVM-instance-stable only and would let two different backend
 * processes derive two different keys for the identical key string).
 *
 * <p>The key is {@code issuer + ":" + subject} — see {@link OidcIdentity}.
 */
@Component
class PostgresOidcIdentityLock implements OidcIdentityLock {

    /** Package-private for asserting in tests if needed, mirroring {@code GuideAdvisoryLock}. */
    static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))";

    private final EntityManager entityManager;

    PostgresOidcIdentityLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void acquire(OidcIdentity id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "OidcIdentityLock.acquire() requires an active transaction: "
                            + "pg_advisory_xact_lock is transaction-scoped and auto-releases on "
                            + "commit/rollback, so calling it outside a transaction would leak the "
                            + "lock for the lifetime of the underlying connection.");
        }
        String key = id.issuer() + ":" + id.subject();
        entityManager.createNativeQuery(LOCK_SQL).setParameter("key", key).getSingleResult();
    }
}
