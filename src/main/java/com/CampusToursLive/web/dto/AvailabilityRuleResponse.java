package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A recurring weekly availability block. Times are local to {@code timezone} (HH:mm). {@code
 * dayOfWeek} is 0=Sunday … 6=Saturday.
 */
@Schema(
        name = "AvailabilityRuleResponse",
        description = "A recurring weekly availability block for a guide.")
public record AvailabilityRuleResponse(
        @Schema(
                        description = "Rule id (UUID).",
                        example = "a1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Day of week (0=Sunday … 6=Saturday).",
                        example = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int dayOfWeek,
        @Schema(
                        description = "Start time in local wall-clock HH:mm.",
                        example = "09:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description = "End time in local wall-clock HH:mm.",
                        example = "17:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String endLocal,
        @Schema(
                        description = "IANA timezone for this rule.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description = "First date this rule applies (ISO-8601 date).",
                        example = "2026-06-01",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String effectiveFrom,
        @Schema(
                        description = "Last date this rule applies, or null if open-ended.",
                        example = "2026-12-31")
                String effectiveTo,
        @Schema(description = "Whether the rule is active for overlap checks.", example = "true")
                boolean active,
        @Schema(
                        description = "Creation timestamp (ISO-8601), or null if unset.",
                        example = "2026-06-01T12:00:00Z")
                String createdAt) {}
