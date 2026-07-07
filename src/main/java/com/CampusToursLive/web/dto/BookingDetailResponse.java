package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A participant's booking, flattened for dashboard display. Combines fields from the booking, its
 * offering, the guide's user record, and the university — assembled by BookingService.
 *
 * <p>{@code status} is the frontend-facing status string (e.g. "WAITING_FOR_GUIDE"), mapped from
 * the internal {@code BookingStatus} by {@code BookingStatus.displayStatus()}.
 *
 * <p>Time fields are ISO-8601 strings (UTC). {@code guideResponseDeadline} is null when the booking
 * has no response deadline (e.g. AUTO acceptance mode).
 */
@Schema(
        name = "BookingDetailResponse",
        description = "A participant booking flattened for dashboard display.")
public record BookingDetailResponse(
        @Schema(
                        description = "Booking id (UUID).",
                        example = "b1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description =
                                "Frontend-facing booking status mapped from the internal"
                                        + " BookingStatus.",
                        example = "WAITING_FOR_GUIDE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description = "ISO-8601 UTC start time of the tour.",
                        example = "2026-08-01T15:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String scheduledAt,
        @Schema(
                        description = "IANA timezone the tour is scheduled in.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description = "Id of the tour offering this booking is for.",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringId,
        @Schema(
                        description = "Display title of the booked offering.",
                        example = "North Campus highlights",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringTitle,
        @Schema(
                        description = "Display name of the guide leading the tour.",
                        example = "Maya Chen",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideName,
        @Schema(
                        description =
                                "ISO-8601 UTC deadline for the guide to respond; null when the"
                                        + " booking has no response deadline (e.g. AUTO"
                                        + " acceptance).",
                        example = "2026-07-30T15:00:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String guideResponseDeadline,
        @Schema(
                        description = "Name of the university the tour is at.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityName,
        @Schema(
                        description = "Tour duration in minutes.",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int durationMin,
        @Schema(
                        description = "Price in the smallest currency unit (cents).",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long priceCents,
        @Schema(
                        description = "ISO-4217 currency code for priceCents.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currency) {}
