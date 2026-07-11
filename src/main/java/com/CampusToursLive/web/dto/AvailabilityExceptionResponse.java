package com.CampusToursLive.web.dto;

/**
 * One of a guide's one-off availability exceptions — the editable unit of {@code
 * /availability/exceptions}.
 */
public record AvailabilityExceptionResponse(
        String id,
        String exceptionDate,
        String kind,
        String startLocal,
        int windowMin,
        String reason) {}
