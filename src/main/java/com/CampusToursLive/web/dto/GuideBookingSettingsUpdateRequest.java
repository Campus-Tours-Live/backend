package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Body for {@code PATCH /availability/settings} — a partial update: a {@code null} field leaves
 * that setting unchanged. When {@code timezone} changes, the write service cascades the new zone
 * onto every one of the guide's existing rules (the read-only-tz invariant: a rule's timezone
 * always equals the guide's settings timezone) and re-projects.
 */
@Schema(
        name = "GuideBookingSettingsUpdateRequest",
        description =
                "Partial update for a guide's booking settings; a null/omitted field leaves that"
                        + " setting unchanged.")
public record GuideBookingSettingsUpdateRequest(
        @Schema(
                        description =
                                "AUTO confirms a booking immediately; MANUAL requires the guide to"
                                        + " accept within responseDeadlineMin.",
                        example = "MANUAL",
                        allowableValues = {"AUTO", "MANUAL"},
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String acceptanceMode,
        @Schema(
                        description = "Minutes a MANUAL-mode guide has to accept/decline; > 0.",
                        example = "90",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer responseDeadlineMin,
        @Schema(
                        description =
                                "Minimum minutes of notice required before a bookable slot; >= 0.",
                        example = "1440",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer minNoticeMin,
        @Schema(
                        description =
                                "Maximum days in advance a slot may be booked; 1-365 (global cap).",
                        example = "30",
                        minimum = "1",
                        maximum = "365",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer maxAdvanceDays,
        @Schema(
                        description =
                                "Buffer minutes reserved immediately before a booked slot; >= 0.",
                        example = "0",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer bufferBeforeMin,
        @Schema(
                        description =
                                "Buffer minutes reserved immediately after a booked slot; >= 0.",
                        example = "15",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer bufferAfterMin,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Tour durations (minutes) this guide offers —"
                                                        + " orthogonal to windowMin; must not be"
                                                        + " empty when provided.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema = @Schema(description = "Duration in minutes.", example = "60"))
                List<Integer> durationsOffered,
        @Schema(
                        description =
                                "IANA timezone; cascades onto every one of the guide's existing"
                                        + " rules on change.",
                        example = "America/New_York",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String timezone) {}
