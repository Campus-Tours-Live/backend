package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code availability_exceptions} table (V4__availability_engine_v2.sql) — a one-off,
 * per-date override to the recurring rules: either {@code UNAVAILABLE} (block) or {@code
 * ADDITIONAL} (add), both expressed as start + duration like the rules. These are net-available
 * INPUTS to the projection (CTL-54 Task 2), applied left-to-right with {@code ADDITIONAL}
 * overriding {@code UNAVAILABLE} on overlap, then coalesced into the materialized occurrences.
 */
@Getter
@Setter
@Entity
@Table(name = "availability_exceptions")
public class AvailabilityExceptionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", columnDefinition = "availability_exception_kind", nullable = false)
    private AvailabilityExceptionKind kind;

    @Column(name = "start_local", nullable = false)
    private LocalTime startLocal;

    /** Availability-WINDOW length in minutes — NOT a tour length. */
    @Column(name = "window_min", nullable = false)
    private int windowMin;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
