package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RescheduleProposalResponse", description = "Proposal to move a CONFIRMED booking.")
public record RescheduleProposalResponse(
        @Schema(description = "Proposal id.", example = "r1a2c3d4-0000-4000-8000-000000000001")
                String id,
        @Schema(description = "Booking id.", example = "b1a2c3d4-0000-4000-8000-000000000001")
                String bookingId,
        @Schema(
                        description = "Who proposed.",
                        example = "PARTICIPANT",
                        allowableValues = {"PARTICIPANT", "GUIDE"})
                String requestedBy,
        @Schema(description = "Proposal status.", example = "PENDING_COUNTERPARTY") String status,
        @Schema(description = "Proposed start (UTC).", example = "2026-08-05T17:00:00Z")
                String proposedStartAt,
        @Schema(description = "Proposed end (UTC).", example = "2026-08-05T18:00:00Z")
                String proposedEndAt,
        @Schema(description = "Fee cents (0 in MVP).", example = "0") long feeCents,
        @Schema(description = "Price diff cents (0 in MVP).", example = "0") long priceDiffCents,
        @Schema(description = "Expiry instant (UTC).", example = "2026-08-03T17:00:00Z")
                String expiresAt) {}
