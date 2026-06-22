package com.CampusToursLive.domain.guide;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideProfileRepository extends JpaRepository<GuideProfileEntity, UUID> {
    Optional<GuideProfileEntity> findByUserId(UUID userId);
}
