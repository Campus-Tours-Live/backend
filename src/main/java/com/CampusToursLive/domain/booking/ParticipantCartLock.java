package com.CampusToursLive.domain.booking;

import java.util.UUID;

/**
 * Per-participant cart serialization lock (CTL-83 review follow-up).
 *
 * <p>DRAFT rows are outside the booking exclusion constraints and have no unique key, so two
 * concurrent add-to-cart requests for the same item could both miss the idempotency lookup and both
 * insert. Holding this lock for the whole add-to-cart transaction makes the lookup, the max-items
 * cap check and the insert one atomic step per participant. Different participants never block each
 * other.
 */
public interface ParticipantCartLock {

    /**
     * Blocks until the transaction-scoped lock for {@code participantUserId} is granted on the
     * CURRENT transaction. It releases automatically on commit or rollback, and re-acquiring it in
     * the same transaction is a harmless re-entrant no-op.
     *
     * @throws IllegalStateException if called without an active transaction on the current thread
     */
    void acquire(UUID participantUserId);
}
