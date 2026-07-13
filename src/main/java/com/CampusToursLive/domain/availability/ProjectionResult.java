package com.CampusToursLive.domain.availability;

import java.time.LocalDate;
import java.util.List;

/**
 * The pure output of {@link AvailabilityProjection#project}: the guide's net-available UTC
 * intervals — disjoint, ascending by {@code startAt} — plus the calendar dates on which a DST
 * transition actually affected a projected window (see {@link AvailabilityProjection} for the exact
 * detection rule).
 */
public record ProjectionResult(
        List<AvailabilityInterval> intervals, List<LocalDate> dstAdjustedDays) {}
