package com.CampusToursLive.domain.booking;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PostgreSQL {@link ParticipantCartLock}: {@code pg_advisory_xact_lock(hashtextextended(key, 0))},
 * the same pattern as {@code GuideAdvisoryLock} and {@code PostgresOidcIdentityLock}.
 *
 * <p>The key is {@code "cart:" + participantUserId}. The {@code cart:} prefix keeps it in its own
 * namespace, so it never shares a 64-bit key with the per-guide lock (keyed on a bare UUID) for a
 * user who is both a guide and a participant.
 */
@Component
class PostgresParticipantCartLock implements ParticipantCartLock {

    static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))";

    static final String KEY_PREFIX = "cart:";

    private final EntityManager entityManager;

    PostgresParticipantCartLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void acquire(UUID participantUserId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "ParticipantCartLock.acquire() requires an active transaction: "
                            + "pg_advisory_xact_lock is transaction-scoped, so outside a"
                            + " transaction it would be released as soon as the statement ends"
                            + " and serialize nothing.");
        }
        entityManager
                .createNativeQuery(LOCK_SQL)
                .setParameter("key", KEY_PREFIX + participantUserId)
                .getSingleResult();
    }
}
