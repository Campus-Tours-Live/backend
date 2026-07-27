package com.CampusToursLive.domain.university;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<UniversityEntity, UUID> {

    /** Look up a university by its stable slug (used to dedupe live-directory upserts). */
    Optional<UniversityEntity> findBySlug(String slug);

    /**
     * Look up a university by its exact name (used to absorb a legacy seed row into a Scorecard
     * upsert instead of creating a duplicate). {@code findFirst} tolerates an accidental duplicate
     * name rather than throwing.
     */
    Optional<UniversityEntity> findFirstByName(String name);
}
