package com.CampusToursLive.domain.availability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideAvailabilityOccurrenceRepository
        extends JpaRepository<GuideAvailabilityOccurrenceEntity, UUID> {

    /** A guide's materialized occurrences, chronological — the resolved read model (Task 5b). */
    List<GuideAvailabilityOccurrenceEntity> findByGuideIdOrderByDuringStartAtAsc(UUID guideId);

    /**
     * Wholesale delete of a guide's occurrences — the "replace" half of rematerialize (Task 3):
     * this is a derived cache, never edited row-by-row.
     */
    long deleteByGuideId(UUID guideId);

    /**
     * Booking-availability CONTAINMENT (CTL-54 Task 6) — {@code true} iff some current occurrence
     * for this guide fully contains the given interval: {@code occurrence.during @> [schedStart,
     * schedEnd)}. This checks the booking's SCHEDULED tour interval, never the (buffer-padded)
     * reserved interval, and is a containment query, never an EXCLUDE — the inverse relationship
     * from availability-vs-availability (gist backstop) and booking-vs-booking
     * (excl_guide_no_overlap) overlap checks.
     *
     * <p>{@code tstzrange} defaults to half-open bounds {@code [)}, matching how {@code
     * schedStart}/{@code schedEnd} (and {@code duringStartAt}/{@code duringEndAt}) are already used
     * elsewhere in this schema.
     */
    @Query(
            value =
                    "select exists(select 1 from guide_availability_occurrences "
                            + "where guide_id = :guideId "
                            + "and tstzrange(during_start_at, during_end_at) @> tstzrange(:schedStart,"
                            + " :schedEnd))",
            nativeQuery = true)
    boolean existsContaining(
            @Param("guideId") UUID guideId,
            @Param("schedStart") Instant schedStart,
            @Param("schedEnd") Instant schedEnd);
}
