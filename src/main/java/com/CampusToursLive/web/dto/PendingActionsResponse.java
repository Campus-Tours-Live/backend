package com.CampusToursLive.web.dto;

/**
 * Counts of outstanding participant actions, assembled by BookingService from BookingRepository
 * counts. Sent as part of the dashboard payload.
 */
public record PendingActionsResponse(
        long paymentsToFinish, long waitingForGuide, long reviewsToWrite) {}
