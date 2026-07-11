package com.CampusToursLive.web.dto;

/**
 * One of a guide's recurring availability rules — the editable unit of {@code /availability/rules}.
 */
public record AvailabilityRuleResponse(
        String id,
        int dayOfWeek,
        String startLocal,
        int windowMin,
        String timezone,
        String effectiveFrom,
        String effectiveTo,
        boolean active) {}
