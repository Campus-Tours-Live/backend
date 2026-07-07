package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PATCH /guide/availability/rules/{ruleId} — all fields optional. */
@Schema(
        name = "UpdateAvailabilityRuleRequest",
        description = "Partial update for a recurring availability rule.")
public record UpdateAvailabilityRuleRequest(
        @Schema(description = "Day of week (0=Sunday … 6=Saturday).", example = "1")
                Integer dayOfWeek,
        @Schema(
                        description = "Start time in local wall-clock HH:mm or HH:mm:ss.",
                        example = "09:00")
                String startLocal,
        @Schema(description = "End time in local wall-clock HH:mm or HH:mm:ss.", example = "17:00")
                String endLocal,
        @Schema(
                        description = "IANA timezone; must match guide booking settings.",
                        example = "America/Los_Angeles")
                String timezone,
        @Schema(
                        description = "First date this rule applies (ISO-8601 date).",
                        example = "2026-06-01")
                String effectiveFrom,
        @Schema(
                        description = "Last date this rule applies (ISO-8601 date), or null.",
                        example = "2026-12-31")
                String effectiveTo,
        @Schema(description = "Whether the rule is active.", example = "true") Boolean active) {}
