package com.CampusToursLive.domain.availability;

import java.time.Instant;

/**
 * An immutable, half-open UTC interval {@code [startAt, endAt)} — the output shape of {@link
 * AvailabilityProjection}. Maps 1:1 onto {@code
 * guide_availability_occurrences.during_start_at}/{@code during_end_at} so Task 3 (persistence) can
 * copy each interval straight onto those two columns.
 */
public record AvailabilityInterval(Instant startAt, Instant endAt) {

    public AvailabilityInterval {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("startAt and endAt must not be null");
        }
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException(
                    "startAt must be strictly before endAt: " + startAt + " >= " + endAt);
        }
    }
}
