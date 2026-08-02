package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code guide_availability_dst_notices} table (V5__availability_dst_notices.sql) — one
 * row per calendar date on which a daylight-saving transition shifted or absorbed one of a guide's
 * availability windows during projection (see {@link ProjectionResult#dstAdjustedDays()}).
 *
 * <p>A DERIVED cache like {@link GuideAvailabilityOccurrenceEntity}: wholesale re-derived and
 * replaced per guide on every {@code rematerialize}, never edited row-by-row. The
 * resolved-availability read model (Task 5b) reads these to warn the guide.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_availability_dst_notices")
public class GuideAvailabilityDstNoticeEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "adjusted_date", nullable = false)
    private LocalDate adjustedDate;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
