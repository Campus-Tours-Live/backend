package com.CampusToursLive.web.dto;

/** POST /guide/availability/exceptions */
public record CreateAvailabilityExceptionRequest(
        String exceptionDate, String type, String startLocal, String endLocal, String reason) {}
