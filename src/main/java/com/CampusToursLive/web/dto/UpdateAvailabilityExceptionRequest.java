package com.CampusToursLive.web.dto;

/** PATCH /guide/availability/exceptions/{exceptionId} — all fields optional. */
public record UpdateAvailabilityExceptionRequest(
        String exceptionDate, String type, String startLocal, String endLocal, String reason) {}
