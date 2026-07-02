package com.CampusToursLive.domain.availability;

/** Matches PostgreSQL {@code availability_exception_type} (V1__schema.sql). */
public enum AvailabilityExceptionType {
    UNAVAILABLE_ALL_DAY,
    UNAVAILABLE_RANGE,
    ADDITIONAL
}
