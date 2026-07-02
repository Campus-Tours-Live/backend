package com.CampusToursLive.web.dto;

import java.util.List;

/** GET /guide/availability — recurring rules, exceptions, and booking policy. */
public record AvailabilitySummaryResponse(
        List<AvailabilityRuleResponse> rules,
        List<AvailabilityExceptionResponse> exceptions,
        BookingSettingsResponse bookingSettings) {}
