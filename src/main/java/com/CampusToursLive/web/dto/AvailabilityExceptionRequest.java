package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body for {@code POST /availability/exceptions} and {@code PATCH /availability/exceptions/{id}}.
 * {@code kind} is {@code UNAVAILABLE} or {@code ADDITIONAL} (see {@link
 * com.CampusToursLive.domain.availability.AvailabilityExceptionKind}); {@code startLocal} is a
 * plain wall-clock time. Under the start+duration model there is no separate {@code ALL_DAY} kind —
 * an all-day block is {@code UNAVAILABLE} with {@code startLocal="00:00"}, {@code windowMin=1440}.
 *
 * <p><b>Single date vs. multi-day (CTL-54 v2.1 Task 3).</b> A caller supplies EITHER {@code
 * exceptionDate} (a single date) OR both {@code dateFrom}/{@code dateTo} (an inclusive multi-day
 * range, applied per-date in one transaction, capped at 366 days) — never a mix. Whichever date(s)
 * are targeted, an override newest-wins trims/replaces any existing same-date exception segment it
 * covers, so stored same-date exceptions stay non-overlapping.
 */
@Schema(
        name = "AvailabilityExceptionRequest",
        description = "Body to create/update a guide's one-off availability exception.")
public record AvailabilityExceptionRequest(
        @Schema(
                        description =
                                "ISO-8601 date the exception applies to (single-date form)."
                                        + " Mutually exclusive with dateFrom/dateTo.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String exceptionDate,
        @Schema(
                        description =
                                "UNAVAILABLE removes availability; ADDITIONAL adds availability."
                                        + " An all-day UNAVAILABLE block is startLocal=\"00:00\","
                                        + " windowMin=1440 (there is no separate ALL_DAY kind).",
                        example = "ADDITIONAL",
                        allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kind,
        @Schema(
                        description = "Wall-clock start time in the guide's settings timezone.",
                        example = "10:00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String startLocal,
        @Schema(
                        description = "Window length in minutes.",
                        example = "60",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer windowMin,
        @Schema(
                        description = "Optional free-text reason for the exception.",
                        example = "extra hours",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reason,
        @Schema(
                        description =
                                "ISO-8601 inclusive start date of a multi-day override. Requires"
                                        + " dateTo; mutually exclusive with exceptionDate. Capped"
                                        + " at 366 days from dateTo.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String dateFrom,
        @Schema(
                        description =
                                "ISO-8601 inclusive end date of a multi-day override. Requires"
                                        + " dateFrom; mutually exclusive with exceptionDate.",
                        example = "2026-07-14",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String dateTo) {

    /**
     * Legacy single-date constructor (pre-Task-3 shape) — kept so existing callers/tests
     * constructing with the original 5 fields keep compiling; {@code dateFrom}/{@code dateTo}
     * default to {@code null} (single-date mode).
     */
    public AvailabilityExceptionRequest(
            String exceptionDate,
            String kind,
            String startLocal,
            Integer windowMin,
            String reason) {
        this(exceptionDate, kind, startLocal, windowMin, reason, null, null);
    }
}
