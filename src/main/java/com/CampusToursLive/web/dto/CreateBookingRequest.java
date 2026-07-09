package com.CampusToursLive.web.dto;

/**
 * Request body for {@code POST /bookings}. {@code scheduledStartAt} is an ISO-8601 instant (e.g.
 * {@code 2026-07-10T17:00:00Z}) -- the absolute UTC instant is the only time on the contract;
 * clients render it in the viewer's local timezone.
 */
public record CreateBookingRequest(
        String tourOfferingId, String scheduledStartAt, String participantNotes) {}
