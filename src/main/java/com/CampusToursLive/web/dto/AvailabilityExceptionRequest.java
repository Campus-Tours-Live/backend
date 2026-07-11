package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body for {@code POST /availability/exceptions} and {@code PATCH /availability/exceptions/{id}}.
 * {@code exceptionDate} is an ISO-8601 date; {@code kind} is {@code UNAVAILABLE} or {@code
 * ADDITIONAL} (see {@link com.CampusToursLive.domain.availability.AvailabilityExceptionKind});
 * {@code startLocal} is a plain wall-clock time. Under the start+duration model there is no
 * separate {@code ALL_DAY} kind — an all-day block is {@code UNAVAILABLE} with {@code
 * startLocal="00:00"}, {@code windowMin=1440}.
 */
@Schema(
        name = "AvailabilityExceptionRequest",
        description = "Body to create/update a guide's one-off availability exception.")
public record AvailabilityExceptionRequest(
        @Schema(
                        description = "ISO-8601 date the exception applies to.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
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
                String reason) {}
