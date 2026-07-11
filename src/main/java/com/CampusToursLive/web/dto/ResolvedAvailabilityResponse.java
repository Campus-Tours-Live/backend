package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * The guide-facing resolved-availability read ({@code GET /availability}, CTL-54 Task 5b) -- the
 * single contract CTL-55/CTL-56 consume for the "actual availability" preview: the guide's editable
 * {@code rules}, the backend-coalesced {@code occurrences} for the requested window (ascending,
 * disjoint, UTC instants), and the {@code dstGapDays} the projection reported. The frontend renders
 * this read-only and does NOT re-coalesce -- this is the single source of truth.
 */
public record ResolvedAvailabilityResponse(
        List<AvailabilityRuleResponse> rules,
        List<ResolvedOccurrence> occurrences,
        List<String> dstGapDays) {}
