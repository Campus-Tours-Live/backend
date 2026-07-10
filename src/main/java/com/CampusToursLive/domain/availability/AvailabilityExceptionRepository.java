package com.CampusToursLive.domain.availability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository
        extends JpaRepository<AvailabilityExceptionEntity, UUID> {

    List<AvailabilityExceptionEntity> findByGuideIdOrderByExceptionDateAscCreatedAtAsc(
            UUID guideId);

    List<AvailabilityExceptionEntity> findByGuideIdAndExceptionDate(
            UUID guideId, LocalDate exceptionDate);

    Optional<AvailabilityExceptionEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
