package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Full mapping of the {@code guide_booking_settings} table (V1__schema.sql) — a guide's booking
 * policy (1:1 with {@code guide_profiles}, keyed by {@code guide_id}). Table already exists, so
 * this entity adds no migration.
 *
 * <p>Originally (Task 3) this mapped only {@code guide_id} + {@code timezone} — the columns
 * rematerialize needed to read. CTL-54 Task 5 extends it to every column so the {@code
 * /availability/settings} read/write endpoints can round-trip the full row. Every field's Java
 * default mirrors the column's SQL {@code DEFAULT}, so a freshly auto-provisioned row (guide has no
 * settings yet) matches what the DB would have defaulted to.
 *
 * <p>{@code updated_at} is DB-trigger-owned ({@code trg_gset_updated} sets {@code NEW.updated_at =
 * now()} on every UPDATE, and the column default handles INSERT) — mapped read-only here
 * (insertable/updatable = false) like every other {@code created_at}/{@code updated_at} column in
 * this codebase; the write service re-reads the row after a write to reflect the trigger's value.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_booking_settings")
public class GuideBookingSettingsEntity {

    /**
     * The {@code guide_profiles.id} primary key — {@code guide_booking_settings.guide_id} is the
     * PK.
     */
    @Id
    @Column(name = "guide_id")
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_mode", columnDefinition = "acceptance_mode", nullable = false)
    private AcceptanceMode acceptanceMode = AcceptanceMode.MANUAL;

    @Column(name = "response_deadline_min", nullable = false)
    private int responseDeadlineMin = 90;

    @Column(name = "min_notice_min", nullable = false)
    private int minNoticeMin = 1440;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays = 30;

    @Column(name = "buffer_before_min", nullable = false)
    private int bufferBeforeMin = 0;

    @Column(name = "buffer_after_min", nullable = false)
    private int bufferAfterMin = 15;

    /**
     * Tour-length options the guide offers, in minutes — orthogonal to {@code
     * guide_availability_rules.window_min} (the availability-WINDOW length); the two combine only
     * at slot-generation time (Task 8). Converted to/from the {@code jsonb} column via {@link
     * DurationsOfferedConverter}.
     */
    @Convert(converter = DurationsOfferedConverter.class)
    @Column(name = "durations_offered", nullable = false, columnDefinition = "jsonb")
    private List<Integer> durationsOffered = new ArrayList<>(List.of(30, 45, 60, 90));

    /** IANA tz id (e.g. {@code America/Los_Angeles}) — the guide's canonical availability zone. */
    @Column(name = "timezone", nullable = false)
    private String timezone = AvailabilityService.DEFAULT_TIMEZONE;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
