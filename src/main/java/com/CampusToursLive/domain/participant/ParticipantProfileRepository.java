package com.CampusToursLive.domain.participant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantProfileRepository
        extends JpaRepository<ParticipantProfileEntity, UUID> {
    Optional<ParticipantProfileEntity> findByUserId(UUID userId);
}
