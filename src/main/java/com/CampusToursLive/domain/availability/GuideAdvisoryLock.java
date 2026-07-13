package com.CampusToursLive.domain.availability;

import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * Single source of truth for the per-guide, TRANSACTION-scoped PostgreSQL advisory lock key (CTL-54
 * B5). B5 correctness depends on EVERY path that re-derives a guide's occurrences acquiring the
 * SAME 64-bit key, so the derivation lives here exactly once instead of being duplicated in each
 * service — the key can never drift between call sites.
 *
 * <p>The key is {@code hashtextextended(guideId::text, 0)}: a stable 64-bit hash of the guide's
 * canonical UUID string. {@code pg_advisory_xact_lock} is transaction-scoped, so it auto-releases
 * on commit/rollback (no explicit unlock, no leak if the work throws), and re-acquiring the same
 * key later in the same transaction is a harmless re-entrant no-op.
 */
final class GuideAdvisoryLock {

    /** The advisory-lock acquisition SQL. Kept package-private for asserting in tests if needed. */
    static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:guideId AS text), 0))";

    private GuideAdvisoryLock() {}

    /**
     * Acquires the per-guide transaction-scoped advisory lock on the supplied {@link EntityManager}
     * (which must be enlisted in the current transaction). Blocks until the lock is granted.
     */
    static void acquire(EntityManager entityManager, UUID guideId) {
        entityManager
                .createNativeQuery(LOCK_SQL)
                .setParameter("guideId", guideId.toString())
                .getSingleResult();
    }
}
