package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository
        extends JpaRepository<AvailabilityExceptionEntity, UUID> {

    List<AvailabilityExceptionEntity> findByGuideIdOrderByExceptionDateAscCreatedAtAsc(
            UUID guideId);

    Optional<AvailabilityExceptionEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
