package com.CampusToursLive.web.dto;

/**
 * A recurring weekly availability block. Times are local to {@code timezone} (HH:mm). {@code
 * dayOfWeek} is 0=Sunday … 6=Saturday.
 */
public record AvailabilityRuleResponse(
        String id,
        int dayOfWeek,
        String startLocal,
        String endLocal,
        String timezone,
        String effectiveFrom,
        String effectiveTo,
        boolean active,
        String createdAt) {}
