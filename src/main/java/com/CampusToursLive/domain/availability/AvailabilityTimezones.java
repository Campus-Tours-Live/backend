package com.CampusToursLive.domain.availability;

import com.CampusToursLive.error.ValidationException;
import java.time.DateTimeException;
import java.time.ZoneId;

/** Shared timezone validation for availability rules and booking settings. */
final class AvailabilityTimezones {

    private AvailabilityTimezones() {}

    static String validateTimezone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("timezone is required");
        }
        try {
            return ZoneId.of(raw.trim()).getId();
        } catch (DateTimeException ex) {
            throw new ValidationException("Invalid timezone: " + raw);
        }
    }

    /**
     * Rules must share the guide's booking-settings timezone so wall-clock overlap checks are safe.
     */
    static String resolveRuleTimezone(String requested, String settingsTimezone) {
        String guideTimezone = validateTimezone(settingsTimezone);
        if (requested == null || requested.isBlank()) {
            return guideTimezone;
        }
        String ruleTimezone = validateTimezone(requested);
        if (!ruleTimezone.equals(guideTimezone)) {
            throw new ValidationException("Rule timezone must match booking settings timezone");
        }
        return ruleTimezone;
    }
}
