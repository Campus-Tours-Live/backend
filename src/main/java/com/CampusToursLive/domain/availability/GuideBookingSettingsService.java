package com.CampusToursLive.domain.availability;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writable get-or-create for per-guide booking settings (isolated from read-only queries). */
@Service
public class GuideBookingSettingsService {

    private final GuideBookingSettingsRepository bookingSettings;

    public GuideBookingSettingsService(GuideBookingSettingsRepository bookingSettings) {
        this.bookingSettings = bookingSettings;
    }

    /**
     * Returns existing settings or inserts defaults. Runs in a new writable transaction so callers
     * inside {@code @Transactional(readOnly = true)} (e.g. GET summary) can persist first-time
     * rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GuideBookingSettingsEntity getOrCreate(UUID guideId) {
        return bookingSettings.findById(guideId).orElseGet(() -> createDefault(guideId));
    }

    private GuideBookingSettingsEntity createDefault(UUID guideId) {
        try {
            GuideBookingSettingsEntity created = new GuideBookingSettingsEntity();
            created.setGuideId(guideId);
            return bookingSettings.save(created);
        } catch (DataIntegrityViolationException ex) {
            return bookingSettings
                    .findById(guideId)
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Failed to load booking settings after concurrent create",
                                            ex));
        }
    }
}
