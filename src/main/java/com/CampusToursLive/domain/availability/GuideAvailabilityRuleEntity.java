package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code guide_availability_rules} table (V1__schema.sql) — a guide's recurring weekly
 * availability, encoded as **start + duration** (RFC 5545 / DTSTART+DURATION style): {@code
 * start_local} + {@code window_min}, never {@code start_local} + an end column. There is no 24:00
 * sentinel and no wraparound — a window that crosses midnight (e.g. start 22:00, window 240 min) is
 * expressed the same way as any other window; only the projection layer (CTL-54 Task 2) turns it
 * into a concrete UTC instant range.
 *
 * <p>{@code windowMin} is the length of the AVAILABILITY WINDOW — orthogonal to {@code
 * tour_offerings.duration_min} / {@code guide_booking_settings.durations_offered} (the tour
 * lengths). The two combine only at slot-generation time (Task 8).
 */
@Getter
@Setter
@Entity
@Table(name = "guide_availability_rules")
public class GuideAvailabilityRuleEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    /** 0 = Sunday .. 6 = Saturday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_local", nullable = false)
    private LocalTime startLocal;

    /** Availability-WINDOW length in minutes — NOT a tour length. */
    @Column(name = "window_min", nullable = false)
    private int windowMin;

    /** IANA tz id; kept equal to {@code guide_booking_settings.timezone} (existing invariant). */
    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
