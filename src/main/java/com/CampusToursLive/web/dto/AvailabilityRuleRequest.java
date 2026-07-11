package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body for {@code POST /availability/rules} and {@code PATCH /availability/rules/{id}}. {@code
 * timezone} is intentionally NOT a field here: a rule's timezone always equals the guide's {@code
 * guide_booking_settings.timezone} (the read-only-tz invariant) — set by the server, never by the
 * client. {@code startLocal} is a plain wall-clock time (e.g. {@code "09:00"}, {@code "09:00:00"});
 * {@code effectiveFrom}/{@code effectiveTo} are ISO-8601 dates (e.g. {@code "2026-07-11"}). All
 * fields are validated (and parsed) by the service, not by the framework, so a bad value maps to a
 * domain 422 rather than a framework 400.
 */
@Schema(
        name = "AvailabilityRuleRequest",
        description =
                "Body to create/update a guide's recurring availability rule (start + duration).")
public record AvailabilityRuleRequest(
        @Schema(
                        description = "Day of week the rule recurs on: 0 (Sunday) - 6 (Saturday).",
                        example = "1",
                        minimum = "0",
                        maximum = "6",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer dayOfWeek,
        @Schema(
                        description = "Wall-clock start time in the guide's settings timezone.",
                        example = "09:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description =
                                "Availability-window length in minutes (NOT the tour"
                                        + " duration/durationsOffered).",
                        example = "60",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer windowMin,
        @Schema(
                        description =
                                "ISO-8601 date the rule starts applying; defaults to today when"
                                        + " omitted on create.",
                        example = "2026-07-11",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String effectiveFrom,
        @Schema(
                        description =
                                "ISO-8601 date the rule stops applying; null/omitted means no end"
                                        + " date.",
                        example = "2026-12-31",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String effectiveTo,
        @Schema(
                        description = "Whether the rule is active; defaults to true when omitted.",
                        example = "true",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Boolean active) {}
