package com.CampusToursLive.domain.availability;

/**
 * Matches the PostgreSQL enum type {@code availability_exception_kind} (V1__schema.sql).
 *
 * <p>Under the start+duration model there is no separate {@code ALL_DAY} kind: "all-day
 * unavailable" is simply {@code UNAVAILABLE} with {@code start_local = 00:00, window_min = 1440}.
 */
public enum AvailabilityExceptionKind {
    UNAVAILABLE,
    ADDITIONAL
}
