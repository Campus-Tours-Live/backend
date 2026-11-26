package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One row from the booking status audit trail, guide-facing. */
@Schema(name = "GuideBookingStatusEventResponse")
public record GuideBookingStatusEventResponse(
        @Schema(
                        description = "Frontend-facing status after the transition.",
                        example = "CONFIRMED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description =
                                "Frontend-facing status before the transition; null for creation.",
                        example = "WAITING_FOR_GUIDE",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String previousStatus,
        @Schema(
                        description = "Who triggered the transition.",
                        example = "GUIDE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String actor,
        @Schema(
                        description = "Machine reason code for the transition.",
                        example = "GUIDE_ACCEPTED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reasonCode,
        @Schema(
                        description = "ISO-8601 UTC timestamp when the transition occurred.",
                        example = "2026-08-01T12:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String occurredAt) {}
