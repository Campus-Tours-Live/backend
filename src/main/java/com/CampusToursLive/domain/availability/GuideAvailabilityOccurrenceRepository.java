package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideAvailabilityOccurrenceRepository
        extends JpaRepository<GuideAvailabilityOccurrenceEntity, UUID> {

    /** A guide's materialized occurrences, chronological — the resolved read model (Task 5b). */
    List<GuideAvailabilityOccurrenceEntity> findByGuideIdOrderByDuringStartAtAsc(UUID guideId);

    /**
     * Wholesale delete of a guide's occurrences — the "replace" half of rematerialize (Task 3):
     * this is a derived cache, never edited row-by-row.
     */
    long deleteByGuideId(UUID guideId);
}
