package com.CampusToursLive.web.dto;

import java.util.List;

/** PATCH /guide/availability/booking-settings — all fields optional. */
public record UpdateBookingSettingsRequest(
        String acceptanceMode,
        Integer responseDeadlineMin,
        Integer minNoticeMin,
        Integer maxAdvanceDays,
        Integer bufferBeforeMin,
        Integer bufferAfterMin,
        List<Integer> durationsOffered,
        String timezone) {}
