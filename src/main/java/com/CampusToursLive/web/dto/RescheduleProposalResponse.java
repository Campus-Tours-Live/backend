package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reschedule proposal, as returned by the propose endpoint (CTL-50) and the coming resolve
 * endpoints (CTL-51). Time fields are ISO-8601 UTC strings; money fields are cents (zero in the MVP
 * — no payments yet).
 */
@Schema(
        name = "RescheduleProposalResponse",
        description = "A proposal to move a CONFIRMED booking to a new time.")
public record RescheduleProposalResponse(
        @Schema(
                        description = "Proposal id (UUID).",
                        example = "r1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Id of the booking this proposal wants to move.",
                        example = "b1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String bookingId,
        @Schema(
                        description = "Which party proposed the new time.",
                        example = "PARTICIPANT",
                        allowableValues = {"PARTICIPANT", "GUIDE"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String requestedBy,
        @Schema(
                        description = "Proposal lifecycle status.",
                        example = "PENDING_COUNTERPARTY",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description = "Proposed new ISO-8601 UTC start time.",
                        example = "2026-08-05T17:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String proposedStartAt,
        @Schema(
                        description =
                                "Proposed new ISO-8601 UTC end time (start + the booking's"
                                        + " duration).",
                        example = "2026-08-05T18:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String proposedEndAt,
        @Schema(
                        description = "Reschedule fee in cents (0 in the MVP).",
                        example = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long feeCents,
        @Schema(
                        description =
                                "Price difference vs the original booking in cents (0 in the MVP).",
                        example = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long priceDiffCents,
        @Schema(
                        description =
                                "ISO-8601 UTC instant when the pending proposal expires if the"
                                        + " counterparty does not respond.",
                        example = "2026-08-03T17:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String expiresAt) {}
