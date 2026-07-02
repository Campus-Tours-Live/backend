package com.CampusToursLive.web.dto;

import java.util.List;

/** Per-guide booking policy surfaced on the availability page. */
public record BookingSettingsResponse(
        String acceptanceMode,
        int responseDeadlineMin,
        int minNoticeMin,
        int maxAdvanceDays,
        int bufferBeforeMin,
        int bufferAfterMin,
        List<Integer> durationsOffered,
        String timezone) {}
