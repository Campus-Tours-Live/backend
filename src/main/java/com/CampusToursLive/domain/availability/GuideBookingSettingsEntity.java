package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Minimal, READ-ONLY mapping of the existing {@code guide_booking_settings} table (V1__schema.sql)
 * — only the columns CTL-54 Task 3 needs to read: the {@code guide_id} primary key and the guide's
 * IANA {@code timezone}. The table already exists (defined in V1 with sensible column defaults), so
 * this adds no migration and does not alter it.
 *
 * <p>Task 3 reads {@code timezone} to project a guide's exceptions in the guide's own zone (so a
 * guide with exceptions but no active rules still materializes correctly). Task 5 will extend this
 * entity with the remaining columns + the {@code /availability/settings} write endpoints; for now
 * only the read side exists. Every other column on the table has a DB default, so a partial insert
 * (guide_id + timezone) still satisfies the table's NOT NULL constraints.
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

    /** IANA tz id (e.g. {@code America/Los_Angeles}) — the guide's canonical availability zone. */
    @Column(name = "timezone", nullable = false)
    private String timezone;
}
