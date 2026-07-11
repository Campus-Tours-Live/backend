package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response for {@code GET/PATCH /availability/settings}. Always returned — {@code GET}
 * auto-provisions a default row the first time a guide is asked for settings, so a guide always has
 * one.
 */
@Schema(
        name = "GuideBookingSettingsResponse",
        description =
                "A guide's booking settings (acceptance mode, windows, buffers, offered"
                        + " durations, timezone).")
public record GuideBookingSettingsResponse(
        @Schema(
                        description = "Id of the guide profile these settings belong to.",
                        example = "11111111-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideId,
        @Schema(
                        description =
                                "AUTO confirms a booking immediately; MANUAL requires the guide to"
                                        + " accept within responseDeadlineMin.",
                        example = "MANUAL",
                        allowableValues = {"AUTO", "MANUAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String acceptanceMode,
        @Schema(
                        description = "Minutes a MANUAL-mode guide has to accept/decline.",
                        example = "90",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int responseDeadlineMin,
        @Schema(
                        description = "Minimum minutes of notice required before a bookable slot.",
                        example = "1440",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int minNoticeMin,
        @Schema(
                        description =
                                "Maximum days in advance a slot may be booked (capped globally at"
                                        + " 365).",
                        example = "30",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int maxAdvanceDays,
        @Schema(
                        description = "Buffer minutes reserved immediately before a booked slot.",
                        example = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int bufferBeforeMin,
        @Schema(
                        description = "Buffer minutes reserved immediately after a booked slot.",
                        example = "15",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int bufferAfterMin,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Tour durations (minutes) this guide offers —"
                                                        + " orthogonal to windowMin.",
                                        requiredMode = Schema.RequiredMode.REQUIRED),
                        schema = @Schema(description = "Duration in minutes.", example = "60"))
                List<Integer> durationsOffered,
        @Schema(
                        description =
                                "IANA timezone; cascades onto every one of the guide's rules on"
                                        + " change (read-only-tz invariant).",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description = "ISO-8601 UTC instant the settings were last updated.",
                        example = "2026-07-11T00:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String updatedAt) {}
