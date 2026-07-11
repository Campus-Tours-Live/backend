package com.CampusToursLive.web.dto;

/**
 * Body for {@code POST /availability/rules} and {@code PATCH /availability/rules/{id}}. {@code
 * timezone} is intentionally NOT a field here: a rule's timezone always equals the guide's {@code
 * guide_booking_settings.timezone} (the read-only-tz invariant) — set by the server, never by the
 * client. {@code startLocal} is a plain wall-clock time (e.g. {@code "09:00"}, {@code "09:00:00"});
 * {@code effectiveFrom}/{@code effectiveTo} are ISO-8601 dates (e.g. {@code "2026-07-11"}). All
 * fields are validated (and parsed) by the service, not by the framework, so a bad value maps to a
 * domain 422 rather than a framework 400.
 */
public record AvailabilityRuleRequest(
        Integer dayOfWeek,
        String startLocal,
        Integer windowMin,
        String effectiveFrom,
        String effectiveTo,
        Boolean active) {}
