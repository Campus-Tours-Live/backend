package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The guide-facing resolved-availability read ({@code GET /availability}, CTL-54 Task 5b) -- the
 * single contract CTL-55/CTL-56 consume for the "actual availability" preview: the guide's editable
 * {@code rules}, the backend-coalesced {@code occurrences} for the requested window (ascending,
 * disjoint, UTC instants), and the {@code dstGapDays} the projection reported. The frontend renders
 * this read-only and does NOT re-coalesce -- this is the single source of truth.
 */
@Schema(
        name = "ResolvedAvailabilityResponse",
        description =
                "A guide's editable rules plus the backend-coalesced, resolved occurrences and any"
                        + " DST gap-moved/skipped days.")
public record ResolvedAvailabilityResponse(
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "The guide's editable availability rules.",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<AvailabilityRuleResponse> rules,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Coalesced, disjoint, ascending net-available"
                                                        + " occurrences for the requested window.",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<ResolvedOccurrence> occurrences,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "ISO-8601 dates the projection had to gap-move or"
                                                        + " skip due to a DST transition.",
                                        requiredMode = Schema.RequiredMode.REQUIRED),
                        schema = @Schema(example = "2026-03-08"))
                List<String> dstGapDays,
        @Schema(
                        description =
                                "Derived readiness signal: true iff the guide has at least one"
                                        + " materialized occurrence that has not yet ended, i.e. a"
                                        + " participant could book right now.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean bookable,
        @Schema(
                        description =
                                "Derived readiness signal: true iff the guide has at least one"
                                        + " active weekly rule (an expired-but-active rule still"
                                        + " counts; a soft-deleted/inactive rule does not).",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean hasWeeklyHours) {}
