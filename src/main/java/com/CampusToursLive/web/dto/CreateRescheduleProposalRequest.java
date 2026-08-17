package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Propose a new booking start time.")
public record CreateRescheduleProposalRequest(
        @Schema(description = "Proposed start (ISO-8601 UTC).", example = "2026-08-05T17:00:00Z")
                String proposedStartAt,
        @Schema(
                        description =
                                "Viewer IANA timezone for client display only; Core ignores and does not persist it.",
                        example = "America/Los_Angeles")
                String participantTimeZone,
        @Schema(description = "Optional reason (max 1000 chars).", example = "Class moved.")
                String reason) {}
