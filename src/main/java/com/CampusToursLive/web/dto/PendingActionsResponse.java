package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Counts of outstanding participant actions, assembled by BookingService from BookingRepository
 * counts. Sent as part of the dashboard payload.
 */
@Schema(
        name = "PendingActionsResponse",
        description = "Counts of outstanding actions for the participant's dashboard.")
public record PendingActionsResponse(
        @Schema(
                        description = "Bookings awaiting payment by the participant.",
                        example = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long paymentsToFinish,
        @Schema(
                        description = "Bookings awaiting a guide's accept/decline.",
                        example = "2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long waitingForGuide,
        @Schema(
                        description = "Completed tours still needing a review.",
                        example = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long reviewsToWrite) {}
