package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** GET /guide/availability — recurring rules, exceptions, and booking policy. */
@Schema(
        name = "AvailabilitySummaryResponse",
        description = "Guide availability summary: rules, exceptions, and booking settings.")
public record AvailabilitySummaryResponse(
        @Schema(
                        description = "Recurring weekly availability rules.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<AvailabilityRuleResponse> rules,
        @Schema(
                        description = "Date-specific availability overrides.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<AvailabilityExceptionResponse> exceptions,
        @Schema(
                        description = "Per-guide booking policy.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                BookingSettingsResponse bookingSettings) {}
