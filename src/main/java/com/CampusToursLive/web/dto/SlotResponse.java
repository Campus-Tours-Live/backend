package com.CampusToursLive.web.dto;

import java.time.Instant;

/**
 * One concrete, bookable slot for a tour offering (CTL-54 Task 8) — an offering-duration-length
 * slice of a guide's net-available occurrence, with any time already taken by an existing booking
 * (its buffered RESERVED interval, not just the bare scheduled interval) and any time outside the
 * guide's notice/max-advance window already removed. Instants are UTC; Jackson serializes {@link
 * Instant} as ISO-8601 with a trailing {@code Z}. Mirrors {@code ResolvedOccurrence}'s shape.
 */
public record SlotResponse(Instant startAt, Instant endAt) {}
