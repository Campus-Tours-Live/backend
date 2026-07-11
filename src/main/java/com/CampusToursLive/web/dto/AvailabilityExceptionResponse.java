package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of a guide's one-off availability exceptions — the editable unit of {@code
 * /availability/exceptions}.
 */
@Schema(
        name = "AvailabilityExceptionResponse",
        description = "One of a guide's one-off availability exceptions.")
public record AvailabilityExceptionResponse(
        @Schema(
                        description = "Exception id (UUID).",
                        example = "c1a2c3d4-0000-4000-8000-000000000011",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "ISO-8601 date the exception applies to.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String exceptionDate,
        @Schema(
                        description =
                                "UNAVAILABLE removes availability; ADDITIONAL adds availability.",
                        example = "ADDITIONAL",
                        allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kind,
        @Schema(
                        description = "Wall-clock start time in timezone.",
                        example = "10:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description = "Window length in minutes.",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int windowMin,
        @Schema(
                        description = "Optional free-text reason for the exception; may be null.",
                        example = "extra hours",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reason) {}
