package com.CampusToursLive.domain.availability;

import java.time.LocalDate;

/**
 * The projection window {@code [from, toExclusive)} — the set of the guide's local calendar dates
 * that {@link AvailabilityProjection} iterates when evaluating rules ({@code day_of_week} + {@code
 * effective_from}/{@code effective_to}) and exceptions ({@code exception_date}).
 *
 * <p>Dates here are calendar dates, not timezone-qualified instants: a rule/exception is keyed by a
 * plain date, independent of any single rule's IANA timezone. The horizon only decides which dates
 * are evaluated — it does NOT clip the resulting UTC intervals, so a window that starts on the last
 * evaluated day (e.g. a cross-midnight rule) may legitimately produce an interval ending after
 * {@code toExclusive}'s midnight.
 */
public record AvailabilityHorizon(LocalDate from, LocalDate toExclusive) {

    public AvailabilityHorizon {
        if (from == null || toExclusive == null) {
            throw new IllegalArgumentException("from and toExclusive must not be null");
        }
        if (!from.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "from must be strictly before toExclusive: " + from + " >= " + toExclusive);
        }
    }
}
