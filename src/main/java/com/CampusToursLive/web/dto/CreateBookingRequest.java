package com.CampusToursLive.web.dto;

/**
 * Request body for {@code POST /participant/bookings}. {@code scheduledStartAt} is an ISO-8601
 * instant (e.g. {@code 2026-07-10T17:00:00Z}); {@code displayTimezone} is the IANA zone the
 * participant wants times rendered in (e.g. {@code America/Los_Angeles}).
 */
public record CreateBookingRequest(
        String tourOfferingId,
        String scheduledStartAt,
        String displayTimezone,
        String participantNotes) {}
