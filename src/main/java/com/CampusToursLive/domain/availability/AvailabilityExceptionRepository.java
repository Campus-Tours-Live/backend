package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository
        extends JpaRepository<AvailabilityExceptionEntity, UUID> {

    /** All of a guide's exceptions (editable units for the rule-CRUD read model, Task 5/5b). */
    List<AvailabilityExceptionEntity> findByGuideId(UUID guideId);

    /** A guide's own exception — scopes writes/deletes so a guide can only touch their own rows. */
    Optional<AvailabilityExceptionEntity> findByIdAndGuideId(UUID id, UUID guideId);
}
