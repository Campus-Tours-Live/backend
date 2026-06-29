package com.CampusToursLive.web.dto;

/**
 * A participant's booking, flattened for dashboard display. Combines fields from the booking, its
 * offering, the guide's user record, and the university — assembled by BookingService.
 *
 * <p>{@code status} is the frontend-facing status string (e.g. "WAITING_FOR_GUIDE"), mapped from
 * the internal {@code BookingStatus} by {@code BookingStatus.displayStatus()}.
 *
 * <p>Time fields are ISO-8601 strings (UTC). {@code guideResponseDeadline} is null when the booking
 * has no response deadline (e.g. AUTO acceptance mode).
 */
public record BookingDetailResponse(
        String id,
        String status,
        String scheduledAt,
        String timezone,
        String offeringId,
        String offeringTitle,
        String guideName,
        String guideResponseDeadline,
        String universityName,
        int durationMin,
        long priceCents,
        String currency) {}
