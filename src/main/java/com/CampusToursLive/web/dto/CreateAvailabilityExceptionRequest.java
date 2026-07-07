package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** POST /guide/availability/exceptions */
@Schema(
        name = "CreateAvailabilityExceptionRequest",
        description = "Body to create a date-specific availability override.")
public record CreateAvailabilityExceptionRequest(
        @Schema(
                        description = "Calendar date of the override (ISO-8601 date).",
                        example = "2026-07-04",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String exceptionDate,
        @Schema(
                        description = "Exception type.",
                        example = "UNAVAILABLE_ALL_DAY",
                        allowableValues = {
                            "UNAVAILABLE_ALL_DAY",
                            "UNAVAILABLE_RANGE",
                            "ADDITIONAL"
                        },
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String type,
        @Schema(
                        description = "Start time for range types (HH:mm); omit for all-day.",
                        example = "10:00")
                String startLocal,
        @Schema(
                        description = "End time for range types (HH:mm); omit for all-day.",
                        example = "12:00")
                String endLocal,
        @Schema(description = "Optional note.", example = "Holiday") String reason) {}
