package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AvailabilityExceptionRepository
        extends JpaRepository<AvailabilityExceptionEntity, UUID> {

    /** All of a guide's exceptions (editable units for the rule-CRUD read model, Task 5/5b). */
    List<AvailabilityExceptionEntity> findByGuideId(UUID guideId);

    /** A guide's own exception — scopes writes/deletes so a guide can only touch their own rows. */
    Optional<AvailabilityExceptionEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * Every distinct guide id that has at least one exception — the other half of the union {@link
     * OccurrenceHorizonJob} (CTL-54 Task 4) enumerates on each roll-forward tick (see {@link
     * GuideAvailabilityRuleRepository#findDistinctGuideIds()}).
     */
    @Query("select distinct e.guideId from AvailabilityExceptionEntity e")
    List<UUID> findDistinctGuideIds();
}
