package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * Body for {@code PATCH /availability/settings} — a partial update: a {@code null} field leaves
 * that setting unchanged. When {@code timezone} changes, the write service cascades the new zone
 * onto every one of the guide's existing rules (the read-only-tz invariant: a rule's timezone
 * always equals the guide's settings timezone) and re-projects.
 */
public record GuideBookingSettingsUpdateRequest(
        String acceptanceMode,
        Integer responseDeadlineMin,
        Integer minNoticeMin,
        Integer maxAdvanceDays,
        Integer bufferBeforeMin,
        Integer bufferAfterMin,
        List<Integer> durationsOffered,
        String timezone) {}
