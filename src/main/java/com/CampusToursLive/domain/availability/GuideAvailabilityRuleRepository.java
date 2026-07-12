package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GuideAvailabilityRuleRepository
        extends JpaRepository<GuideAvailabilityRuleEntity, UUID> {

    /** All of a guide's rules (editable units for the rule-CRUD read model, Task 5/5b). */
    List<GuideAvailabilityRuleEntity> findByGuideId(UUID guideId);

    /** A guide's own rule — scopes writes/deletes so a guide can only touch their own rows. */
    Optional<GuideAvailabilityRuleEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * A guide's own ACTIVE rules for a single day of week — the sibling candidate set for the
     * same-day-of-week overlap validation (CTL-54 v2.1 Task 2). Inactive rules never conflict.
     */
    List<GuideAvailabilityRuleEntity> findByGuideIdAndDayOfWeekAndActiveTrue(
            UUID guideId, short dayOfWeek);

    /**
     * Every distinct guide id that has at least one rule — half of the union {@link
     * OccurrenceHorizonJob} (CTL-54 Task 4) enumerates on each roll-forward tick (the other half is
     * {@link AvailabilityExceptionRepository#findDistinctGuideIds()}).
     */
    @Query("select distinct r.guideId from GuideAvailabilityRuleEntity r")
    List<UUID> findDistinctGuideIds();
}
