package com.CampusToursLive.domain.reschedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
