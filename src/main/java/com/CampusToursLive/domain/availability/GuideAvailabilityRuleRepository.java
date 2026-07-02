package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideAvailabilityRuleRepository
        extends JpaRepository<GuideAvailabilityRuleEntity, UUID> {

    List<GuideAvailabilityRuleEntity> findByGuideIdOrderByDayOfWeekAscStartLocalAsc(UUID guideId);

    List<GuideAvailabilityRuleEntity> findByGuideIdAndDayOfWeekAndActiveTrue(
            UUID guideId, short dayOfWeek);

    Optional<GuideAvailabilityRuleEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
