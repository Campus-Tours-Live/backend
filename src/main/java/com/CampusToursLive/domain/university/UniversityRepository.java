package com.CampusToursLive.domain.university;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<UniversityEntity, UUID> {

    /** Look up a university by its stable slug (used to dedupe live-directory upserts). */
    Optional<UniversityEntity> findBySlug(String slug);

    Optional<UniversityEntity> findFirstByName(String name);
}
