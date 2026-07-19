package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Propose body. End is derived from booking duration; timezone is display-only. */
@Schema(
        name = "CreateRescheduleProposalRequest",
        description = "Propose a new start time for a CONFIRMED booking.")
public record CreateRescheduleProposalRequest(
        @Schema(
                        description = "Proposed start (ISO-8601 UTC).",
                        example = "2026-08-05T17:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String proposedStartAt,
        @Schema(
                        description = "Viewer IANA timezone (display only).",
                        example = "America/Los_Angeles")
                String participantTimeZone,
        @Schema(description = "Optional reason (max 1000 chars).", example = "Class moved.")
                String reason) {}
