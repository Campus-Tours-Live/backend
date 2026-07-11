package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * Response for {@code GET/PATCH /availability/settings}. Always returned — {@code GET}
 * auto-provisions a default row the first time a guide is asked for settings, so a guide always has
 * one.
 */
public record GuideBookingSettingsResponse(
        String guideId,
        String acceptanceMode,
        int responseDeadlineMin,
        int minNoticeMin,
        int maxAdvanceDays,
        int bufferBeforeMin,
        int bufferAfterMin,
        List<Integer> durationsOffered,
        String timezone,
        String updatedAt) {}
