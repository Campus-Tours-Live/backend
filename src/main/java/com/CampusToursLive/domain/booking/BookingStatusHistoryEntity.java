package com.CampusToursLive.domain.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps {@code booking_status_history} (V1__schema.sql) — the append-only audit trail of every
 * booking status transition. Rows are only ever inserted, never updated. The PK is a DB identity
 * column ({@code GENERATED ALWAYS}), so the id must be left unset on insert.
 */
@Getter
@Setter
@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** Null for the very first transition (creation). */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", columnDefinition = "booking_status")
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", columnDefinition = "booking_status", nullable = false)
    private BookingStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", columnDefinition = "booking_actor", nullable = false)
    private BookingActor actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
