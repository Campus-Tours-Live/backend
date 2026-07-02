package com.CampusToursLive.web.dto;

/** A one-off override to weekly availability. */
public record AvailabilityExceptionResponse(
        String id,
        String exceptionDate,
        String type,
        String startLocal,
        String endLocal,
        String reason,
        String createdAt) {}
