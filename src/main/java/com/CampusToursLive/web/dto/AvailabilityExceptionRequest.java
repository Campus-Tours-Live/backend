package com.CampusToursLive.web.dto;

/**
 * Body for {@code POST /availability/exceptions} and {@code PATCH /availability/exceptions/{id}}.
 * {@code exceptionDate} is an ISO-8601 date; {@code kind} is {@code UNAVAILABLE} or {@code
 * ADDITIONAL} (see {@link com.CampusToursLive.domain.availability.AvailabilityExceptionKind});
 * {@code startLocal} is a plain wall-clock time. Under the start+duration model there is no
 * separate {@code ALL_DAY} kind — an all-day block is {@code UNAVAILABLE} with {@code
 * startLocal="00:00"}, {@code windowMin=1440}.
 */
public record AvailabilityExceptionRequest(
        String exceptionDate, String kind, String startLocal, Integer windowMin, String reason) {}
