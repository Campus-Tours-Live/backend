package com.CampusToursLive.domain.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code bookings} table (V1__schema.sql).
 *
 * <p>Maps the columns the dashboard reads and the create/cancel writes need. Still unmapped: {@code
 * topics} (jsonb, keeps its DB default {@code '[]'} on insert) and {@code started_at} — they belong
 * to the live-session slice.
 */
@Getter
@Setter
@Entity
@Table(name = "bookings")
public class BookingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    /** Human-facing unique reference (e.g. {@code BK-7F3K2M9QX1}), generated at creation. */
    @Column(name = "booking_number", nullable = false, updatable = false)
    private String bookingNumber;

    @Column(name = "participant_user_id", nullable = false)
    private UUID participantUserId;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "tour_offering_id", nullable = false)
    private UUID tourOfferingId;

    @Column(name = "university_id", nullable = false)
    private UUID universityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "booking_status", nullable = false)
    private BookingStatus status;

    /** The guide's acceptance mode frozen at booking time (AUTO confirms, MANUAL waits). */
    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_mode_snap", columnDefinition = "acceptance_mode", nullable = false)
    private AcceptanceMode acceptanceModeSnap;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    /** Guide-reserved interval including buffers — what the DB overlap constraint guards. */
    @Column(name = "reserved_start_at", nullable = false)
    private Instant reservedStartAt;

    @Column(name = "reserved_end_at", nullable = false)
    private Instant reservedEndAt;

    @Column(name = "guide_response_deadline_at")
    private Instant guideResponseDeadlineAt;

    /** Immutable price snapshot — copied from the offering at booking time. */
    @Column(name = "base_price_cents", nullable = false)
    private long basePriceCents;

    @Column(name = "service_fee_cents", nullable = false)
    private long serviceFeeCents;

    @Column(name = "tax_cents", nullable = false)
    private long taxCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents;

    @Column(name = "guide_amount_cents", nullable = false)
    private long guideAmountCents;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "participant_notes")
    private String participantNotes;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_actor", columnDefinition = "booking_actor")
    private BookingActor cancellationActor;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Optimistic locking — maps the {@code version BIGINT NOT NULL DEFAULT 0} column. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
