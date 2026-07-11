package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One backend-coalesced, net-available availability window, exactly as persisted (see {@code
 * GuideAvailabilityOccurrenceEntity}, CTL-54 Task 5b). Instants are UTC; Jackson serializes {@link
 * Instant} as ISO-8601 with a trailing {@code Z}. The frontend/BFF render this AS-IS -- this
 * endpoint is the single source of truth and does not re-coalesce.
 */
@Schema(
        name = "ResolvedOccurrence",
        description =
                "One backend-coalesced, net-available availability window (UTC), rendered"
                        + " as-is by the frontend/BFF.")
public record ResolvedOccurrence(
        @Schema(
                        description = "ISO-8601 UTC start instant of the occurrence.",
                        example = "2026-07-13T16:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant startAt,
        @Schema(
                        description = "ISO-8601 UTC end instant of the occurrence.",
                        example = "2026-07-13T17:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant endAt) {}
