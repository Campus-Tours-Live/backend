package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /bookings/{bookingId}/reschedule-proposals}. {@code proposedStartAt}
 * is an ISO-8601 UTC instant — the absolute instant is the only time on the contract (same
 * convention as {@link CreateBookingRequest}); the proposed end is derived from the booking's
 * duration, never supplied by the client. {@code participantTimeZone} is display metadata only and
 * does not affect validation.
 */
@Schema(
        name = "CreateRescheduleProposalRequest",
        description = "Proposes moving a CONFIRMED booking to a new start time.")
public record CreateRescheduleProposalRequest(
        @Schema(
                        description = "Proposed new ISO-8601 UTC start time of the tour.",
                        example = "2026-08-05T17:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String proposedStartAt,
        @Schema(
                        description =
                                "IANA timezone the proposer was viewing times in (display metadata"
                                        + " only).",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String participantTimeZone,
        @Schema(
                        description = "Optional free-text reason (max 1000 characters).",
                        example = "A class was moved to that morning.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reason) {}
