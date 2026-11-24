package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /availability/rules/active} (CTL-60): turn ONE weekday's recurring
 * availability rules on or off together, atomically. Every rule the guide has on {@code dayOfWeek}
 * is set to {@code active} in one transaction; rules are never deleted (deactivate-preserve), so
 * turning the day back on restores the same ranges. Other weekdays are untouched.
 */
@Schema(
        name = "RulesActiveRequest",
        description =
                "Turn all of one weekday's recurring availability rules on or off together, in one"
                        + " transaction. Rules are never deleted, so turning the day back on"
                        + " restores the same ranges; other weekdays are untouched.")
public record RulesActiveRequest(
        @Schema(
                        description = "Day of week to toggle: 0 (Sunday) - 6 (Saturday).",
                        example = "1",
                        minimum = "0",
                        maximum = "6",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer dayOfWeek,
        @Schema(
                        description =
                                "true to make the day available (activate all of its rules), false"
                                        + " to make it unavailable (deactivate them).",
                        example = "false",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Boolean active) {}
