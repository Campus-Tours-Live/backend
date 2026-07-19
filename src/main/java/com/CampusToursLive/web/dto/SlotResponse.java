package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One concrete, bookable slot for a tour offering (CTL-54 Task 8) — an offering-duration-length
 * slice of a guide's net-available occurrence, with any time already taken by an existing booking
 * (its buffered RESERVED interval, not just the bare scheduled interval) and any time outside the
 * guide's notice/max-advance window already removed. Instants are UTC; Jackson serializes {@link
 * Instant} as ISO-8601 with a trailing {@code Z}. Mirrors {@code ResolvedOccurrence}'s shape.
 */
@Schema(
        name = "SlotResponse",
        description =
                "One concrete, bookable slot: an offering-duration-length window with existing"
                        + " bookings and the notice/max-advance window already removed.")
public record SlotResponse(
        @Schema(
                        description = "ISO-8601 UTC start instant of the slot.",
                        example = "2026-07-14T10:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant startAt,
        @Schema(
                        description = "ISO-8601 UTC end instant of the slot.",
                        example = "2026-07-14T11:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant endAt) {}
