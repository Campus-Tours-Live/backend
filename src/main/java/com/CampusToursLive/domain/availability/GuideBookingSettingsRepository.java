package com.CampusToursLive.domain.availability;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideBookingSettingsRepository
        extends JpaRepository<GuideBookingSettingsEntity, UUID> {}
