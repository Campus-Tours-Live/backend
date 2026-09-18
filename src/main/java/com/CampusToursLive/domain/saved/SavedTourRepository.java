package com.CampusToursLive.domain.saved;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedTourRepository extends JpaRepository<SavedTourEntity, UUID> {

    Optional<SavedTourEntity> findByUserIdAndTourOfferingId(UUID userId, UUID tourOfferingId);

    boolean existsByUserIdAndTourOfferingId(UUID userId, UUID tourOfferingId);

    void deleteByUserIdAndTourOfferingId(UUID userId, UUID tourOfferingId);

    Page<SavedTourEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
