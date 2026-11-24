package com.CampusToursLive.domain.reschedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RescheduleProposalRepository
        extends JpaRepository<RescheduleProposalEntity, UUID> {

    /**
     * The single active proposal for a booking, if any. Backed by the partial unique index {@code
     * uq_reschedule_active}, so at most one {@code PENDING_COUNTERPARTY} row can match — the
     * propose flow uses this to reject a second concurrent proposal with a friendly error.
     */
    Optional<RescheduleProposalEntity> findByBookingIdAndStatus(
            UUID bookingId, RescheduleStatus status);

    /** Full proposal history for a booking, newest first. */
    List<RescheduleProposalEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Flips every {@code PENDING_COUNTERPARTY} proposal whose {@code expires_at} is at or before
     * {@code now} to {@code EXPIRED} and returns how many (CTL-52). One bulk statement, filtered on
     * the pending status: a proposal a concurrent accept/decline already resolved no longer
     * matches, so it is never overwritten, and an expired row drops out of {@code
     * uq_reschedule_active}, so it stops blocking a new proposal. The booking is untouched.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update RescheduleProposalEntity p
               set p.status = com.CampusToursLive.domain.reschedule.RescheduleStatus.EXPIRED
             where p.status
                       = com.CampusToursLive.domain.reschedule.RescheduleStatus.PENDING_COUNTERPARTY
               and p.expiresAt <= :now
            """)
    int expirePastDue(@Param("now") Instant now);
}
