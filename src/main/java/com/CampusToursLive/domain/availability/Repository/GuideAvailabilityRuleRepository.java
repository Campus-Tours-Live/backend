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

    /**
     * Derived readiness signal {@code hasWeeklyHours}: {@code true} iff this guide has at least one
     * ACTIVE weekly rule, regardless of its {@code effective_from}/{@code effective_to} window — an
     * expired-but-active rule still counts (the guide has configured weekly hours), whereas a
     * soft-deleted (inactive) rule does not. A plain Spring-Data existence probe.
     */
    boolean existsByGuideIdAndActiveTrue(UUID guideId);

    /** A guide's own rule — scopes writes/deletes so a guide can only touch their own rows. */
    Optional<GuideAvailabilityRuleEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * A guide's own ACTIVE rules for a single day of week — the sibling candidate set for the
     * same-day-of-week overlap validation. Inactive rules never conflict.
     */
    List<GuideAvailabilityRuleEntity> findByGuideIdAndDayOfWeekAndActiveTrue(
            UUID guideId, short dayOfWeek);

    /**
     * Every distinct guide id that has at least one rule — half of the union {@link
     * OccurrenceHorizonJob} enumerates on each roll-forward tick (the other half is {@link
     * AvailabilityExceptionRepository#findDistinctGuideIds()}).
     */
    @Query("select distinct r.guideId from GuideAvailabilityRuleEntity r")
    List<UUID> findDistinctGuideIds();
}
