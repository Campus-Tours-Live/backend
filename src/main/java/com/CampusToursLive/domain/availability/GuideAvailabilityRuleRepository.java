package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideAvailabilityRuleRepository
        extends JpaRepository<GuideAvailabilityRuleEntity, UUID> {

    /** All of a guide's rules (editable units for the rule-CRUD read model, Task 5/5b). */
    List<GuideAvailabilityRuleEntity> findByGuideId(UUID guideId);

    /** A guide's own rule — scopes writes/deletes so a guide can only touch their own rows. */
    Optional<GuideAvailabilityRuleEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
