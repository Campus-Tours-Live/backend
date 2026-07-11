package com.CampusToursLive.web.dto;

import java.time.Instant;

/**
 * One backend-coalesced, net-available availability window, exactly as persisted (see {@code
 * GuideAvailabilityOccurrenceEntity}, CTL-54 Task 5b). Instants are UTC; Jackson serializes {@link
 * Instant} as ISO-8601 with a trailing {@code Z}. The frontend/BFF render this AS-IS -- this
 * endpoint is the single source of truth and does not re-coalesce.
 */
public record ResolvedOccurrence(Instant startAt, Instant endAt) {}
