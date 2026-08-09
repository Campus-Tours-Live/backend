package com.CampusToursLive.domain.guide;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideUniversityRepository extends JpaRepository<GuideUniversityEntity, UUID> {
    List<GuideUniversityEntity> findByGuideProfileId(UUID guideProfileId);

    List<GuideUniversityEntity> findByGuideProfileIdIn(Collection<UUID> guideProfileIds);
}
