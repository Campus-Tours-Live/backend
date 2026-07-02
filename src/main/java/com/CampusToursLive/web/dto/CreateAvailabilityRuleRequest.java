package com.CampusToursLive.web.dto;

/** POST /guide/availability/rules */
public record CreateAvailabilityRuleRequest(
        Integer dayOfWeek,
        String startLocal,
        String endLocal,
        String timezone,
        String effectiveFrom,
        String effectiveTo,
        Boolean active) {}
