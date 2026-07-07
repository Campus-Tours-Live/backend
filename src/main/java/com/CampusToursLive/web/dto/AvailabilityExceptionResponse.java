package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A one-off override to weekly availability. */
@Schema(
        name = "AvailabilityExceptionResponse",
        description = "A date-specific availability override for a guide.")
public record AvailabilityExceptionResponse(
        @Schema(
                        description = "Exception id (UUID).",
                        example = "e1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
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
                        description = "Start time for range types (HH:mm); null for all-day.",
                        example = "10:00")
                String startLocal,
        @Schema(
                        description = "End time for range types (HH:mm); null for all-day.",
                        example = "12:00")
                String endLocal,
        @Schema(description = "Optional note shown to the guide.", example = "Holiday")
                String reason,
        @Schema(
                        description = "Creation timestamp (ISO-8601), or null if unset.",
                        example = "2026-06-15T12:00:00Z")
                String createdAt) {}
