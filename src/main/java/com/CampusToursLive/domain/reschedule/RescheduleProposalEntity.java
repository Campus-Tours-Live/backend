package com.CampusToursLive.domain.reschedule;

import com.CampusToursLive.domain.booking.BookingActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code reschedule_proposals} table (V1__schema.sql) — one pending-or-resolved proposal
 * to move a booking to a new time slot. At most one {@code PENDING_COUNTERPARTY} row may exist per
 * booking (partial unique index {@code uq_reschedule_active}); resolved rows (accepted / declined /
 * expired / cancelled) are kept as history.
 *
 * <p>{@code created_at} / {@code updated_at} are owned by the DB (default {@code now()} + the
 * {@code trg_resched_updated} trigger), so they are read-only from JPA. The id is assigned in code
 * on insert, mirroring {@link com.CampusToursLive.domain.booking.BookingEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "reschedule_proposals")
public class RescheduleProposalEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** Who initiated the proposal (PARTICIPANT or GUIDE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by", columnDefinition = "booking_actor", nullable = false)
    private BookingActor requestedBy;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "proposed_start_at", nullable = false)
    private Instant proposedStartAt;

    @Column(name = "proposed_end_at", nullable = false)
    private Instant proposedEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "reschedule_status", nullable = false)
    private RescheduleStatus status;

    /** Reschedule fee charged for this proposal, if any (frozen at proposal time). */
    @Column(name = "fee_cents", nullable = false)
    private long feeCents;

    /** Price delta versus the original booking (positive = participant owes more). */
    @Column(name = "price_diff_cents", nullable = false)
    private long priceDiffCents;

    /** When the pending proposal auto-expires if the counterparty does not respond. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
