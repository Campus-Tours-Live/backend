package com.CampusToursLive.domain.tour;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourOfferingRepository extends JpaRepository<TourOfferingEntity, UUID> {

    List<TourOfferingEntity> findByGuideId(UUID guideId);

    boolean existsByGuideIdAndSlug(UUID guideId, String slug);

    Optional<TourOfferingEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
