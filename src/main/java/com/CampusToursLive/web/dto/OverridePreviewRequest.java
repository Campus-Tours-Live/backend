package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query params for {@code GET /availability/preview} (CTL-54 v2.1 Task 4): a proposed date-specific
 * override that has NOT been saved yet. Mirrors the multi-day shape of {@link
 * AvailabilityExceptionRequest} ({@code kind}/{@code startLocal}/{@code windowMin} plus an
 * inclusive date range), except {@code dateFrom}/{@code dateTo} are ALWAYS both required here --
 * there is no single-date shorthand, since this is a read-only {@code GET}.
 *
 * <p>Not bound as a request body (the controller builds it from {@code @RequestParam}s -- see
 * {@link com.CampusToursLive.web.AvailabilityController#getOverridePreview}); the {@link Schema}
 * annotations below document the equivalent query parameters via {@code @Parameter} on the
 * controller method and describe this shape for anyone constructing it directly (e.g. tests).
 */
@Schema(
        name = "OverridePreviewRequest",
        description =
                "A proposed date-specific override that has NOT been saved yet, used to compute a"
                        + " dry-run preview.")
public record OverridePreviewRequest(
        @Schema(
                        description = "ISO-8601 inclusive start date of the previewed range.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String dateFrom,
        @Schema(
                        description = "ISO-8601 inclusive end date of the previewed range.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String dateTo,
        @Schema(
                        description =
                                "UNAVAILABLE removes availability; ADDITIONAL adds availability.",
                        example = "UNAVAILABLE",
                        allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kind,
        @Schema(
                        description = "Wall-clock start time in the guide's settings timezone.",
                        example = "09:30",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description = "Window length in minutes.",
                        example = "90",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer windowMin) {}
