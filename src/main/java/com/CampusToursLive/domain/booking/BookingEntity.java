package com.CampusToursLive.domain.booking;

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
 * Maps the {@code bookings} table (V1__schema.sql).
 *
 * <p>Only the columns needed for dashboard reads are mapped here. Payment breakdown fields
 * (service_fee_cents, tax_cents, etc.), overlap-check intervals (reserved_start_at /
 * reserved_end_at), and optimistic-lock version are intentionally omitted until those features are
 * built.
 */
@Getter
@Setter
@Entity
@Table(name = "bookings")
public class BookingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

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

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Column(name = "display_timezone", nullable = false)
    private String displayTimezone;

    @Column(name = "guide_response_deadline_at")
    private Instant guideResponseDeadlineAt;

    /** Immutable price snapshot — copied from the offering at booking time. */
    @Column(name = "base_price_cents", nullable = false)
    private long basePriceCents;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
