package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PATCH /guide/availability/exceptions/{exceptionId} — all fields optional. */
@Schema(
        name = "UpdateAvailabilityExceptionRequest",
        description = "Partial update for a date-specific availability override.")
public record UpdateAvailabilityExceptionRequest(
        @Schema(
                        description = "Calendar date of the override (ISO-8601 date).",
                        example = "2026-07-04")
                String exceptionDate,
        @Schema(
                        description = "Exception type.",
                        example = "UNAVAILABLE_RANGE",
                        allowableValues = {
                            "UNAVAILABLE_ALL_DAY",
                            "UNAVAILABLE_RANGE",
                            "ADDITIONAL"
                        })
                String type,
        @Schema(description = "Start time for range types (HH:mm).", example = "10:00")
                String startLocal,
        @Schema(description = "End time for range types (HH:mm).", example = "12:00")
                String endLocal,
        @Schema(description = "Optional note.", example = "Updated reason") String reason) {}
