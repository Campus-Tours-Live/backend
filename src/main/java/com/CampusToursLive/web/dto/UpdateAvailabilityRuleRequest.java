package com.CampusToursLive.web.dto;

/** PATCH /guide/availability/rules/{ruleId} — all fields optional. */
public record UpdateAvailabilityRuleRequest(
        Integer dayOfWeek,
        String startLocal,
        String endLocal,
        String timezone,
        String effectiveFrom,
        String effectiveTo,
        Boolean active) {}
