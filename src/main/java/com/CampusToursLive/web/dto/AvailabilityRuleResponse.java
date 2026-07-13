package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of a guide's recurring availability rules — the editable unit of {@code /availability/rules}.
 */
@Schema(
        name = "AvailabilityRuleResponse",
        description = "One of a guide's recurring availability rules (start + duration).")
public record AvailabilityRuleResponse(
        @Schema(
                        description = "Rule id (UUID).",
                        example = "c1a2c3d4-0000-4000-8000-000000000010",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Day of week the rule recurs on: 0 (Sunday) - 6 (Saturday).",
                        example = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int dayOfWeek,
        @Schema(
                        description = "Wall-clock start time in timezone.",
                        example = "09:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description =
                                "Availability-window length in minutes (NOT the tour"
                                        + " duration/durationsOffered).",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int windowMin,
        @Schema(
                        description =
                                "IANA timezone; always equals the guide's settings timezone"
                                        + " (read-only-tz invariant), never set directly.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description = "ISO-8601 date the rule starts applying.",
                        example = "2026-07-11",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String effectiveFrom,
        @Schema(
                        description =
                                "ISO-8601 date the rule stops applying; null means no end date.",
                        example = "2026-12-31",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String effectiveTo,
        @Schema(
                        description = "Whether the rule is currently active.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean active) {}
