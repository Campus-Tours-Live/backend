package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps {@code guide_availability_rules} (V1__schema.sql). */
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

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Convert(converter = LocalWallClockTimeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "start_local", nullable = false, length = 8)
    private LocalTime startLocal;

    @Convert(converter = LocalWallClockTimeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "end_local", nullable = false, length = 8)
    private LocalTime endLocal;

    @Column(name = "timezone", nullable = false)
    private String timezone = "America/Los_Angeles";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
