package com.CampusToursLive.domain.availability;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read side of {@code guide_booking_settings}. {@code guide_id} is the table's primary key, so
 * {@link #findByGuideId(UUID)} is a lookup by id. Task 5 will add the write endpoints on top of
 * this repository.
 */
public interface GuideBookingSettingsRepository
        extends JpaRepository<GuideBookingSettingsEntity, UUID> {

    /** A guide's booking settings row, if one exists (guide_id is the PK). */
    Optional<GuideBookingSettingsEntity> findByGuideId(UUID guideId);
}
