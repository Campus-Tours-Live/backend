package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code guide_availability_occurrences} table (V4__availability_engine_v2.sql) — the
 * materialized, coalesced, net-available projection of a guide's rules + exceptions into concrete
 * UTC instants.
 *
 * <p>This is a DERIVED cache (spec "(G)"): wholesale re-computed and replaced by the persistence
 * layer (CTL-54 Task 3) whenever a rule, exception, or the guide's settings timezone changes —
 * never edited row-by-row. {@code sourceRuleId} / {@code sourceExceptionId} are informational only
 * (after union/difference the net interval doesn't map 1:1 to a single input row); they may be
 * {@code null} and are not a foreign-key-style ownership link.
 *
 * <p>The logical "{@code during tstzrange}" interval is represented as two physical columns, {@code
 * duringStartAt} / {@code duringEndAt} — the same shape this schema already uses for {@code
 * bookings.reserved_start_at}/{@code reserved_end_at} — with the {@code tstzrange} computed inline
 * by the {@code excl_guide_occurrence_no_overlap} GIST exclusion constraint (mirroring {@code
 * excl_guide_no_overlap} on {@code bookings}) rather than stored as a physical range column.
 *
 * <p><b>Invariant (backstop, not a user-facing conflict):</b> {@code EXCLUDE USING gist (guide_id
 * WITH =, tstzrange(during_start_at, during_end_at) WITH &&)} — the projection coalesces the
 * net-available set into a disjoint union before insert, so this should never fire in normal
 * operation.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_availability_occurrences")
public class GuideAvailabilityOccurrenceEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "during_start_at", nullable = false)
    private Instant duringStartAt;

    @Column(name = "during_end_at", nullable = false)
    private Instant duringEndAt;

    /** Informational only (see class Javadoc) — never used as an ownership/authorization link. */
    @Column(name = "source_rule_id")
    private UUID sourceRuleId;

    /** Informational only (see class Javadoc) — never used as an ownership/authorization link. */
    @Column(name = "source_exception_id")
    private UUID sourceExceptionId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
