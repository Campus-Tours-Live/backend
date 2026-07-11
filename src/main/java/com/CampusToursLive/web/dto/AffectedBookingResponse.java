package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A booking that an availability edit left uncovered by any current materialized occurrence (CTL-54
 * Task 7 — "(A) allow + notify"). By the time this is returned the write has ALREADY succeeded and
 * the booking is UNCHANGED: still {@code CONFIRMED}, same {@code scheduledStartAt} / {@code
 * scheduledEndAt}. This is purely advisory so the guide-facing UI can warn the guide — CONFIRMED
 * bookings are immutable and are never retroactively cancelled or mutated by an availability edit.
 */
@Schema(
        name = "AffectedBookingResponse",
        description =
                "A CONFIRMED booking an availability edit left uncovered by any current occurrence;"
                        + " advisory only, the booking itself is untouched.")
public record AffectedBookingResponse(
        @Schema(
                        description = "Booking id (UUID).",
                        example = "b1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String bookingId,
        @Schema(
                        description = "Human-facing booking number.",
                        example = "BK-7F3K2M9QX1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String bookingNumber,
        @Schema(
                        description = "ISO-8601 UTC scheduled start of the tour.",
                        example = "2026-07-13T19:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String scheduledStartAt,
        @Schema(
                        description = "ISO-8601 UTC scheduled end of the tour.",
                        example = "2026-07-13T20:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String scheduledEndAt,
        @Schema(
                        description =
                                "Booking status; always CONFIRMED (the only status this warning"
                                        + " scope covers).",
                        example = "CONFIRMED",
                        allowableValues = {"CONFIRMED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status) {}
