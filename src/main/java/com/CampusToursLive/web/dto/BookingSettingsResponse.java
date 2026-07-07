package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Per-guide booking policy surfaced on the availability page. */
@Schema(
        name = "BookingSettingsResponse",
        description = "Per-guide booking policy returned with availability.")
public record BookingSettingsResponse(
        @Schema(
                        description = "How incoming bookings are accepted.",
                        example = "MANUAL",
                        allowableValues = {"AUTO", "MANUAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String acceptanceMode,
        @Schema(
                        description = "Minutes the guide has to respond to a booking request.",
                        example = "90")
                int responseDeadlineMin,
        @Schema(
                        description = "Minimum notice before a slot can be booked (minutes).",
                        example = "1440")
                int minNoticeMin,
        @Schema(description = "How far ahead participants may book (days).", example = "30")
                int maxAdvanceDays,
        @Schema(description = "Buffer before each tour (minutes).", example = "0")
                int bufferBeforeMin,
        @Schema(description = "Buffer after each tour (minutes).", example = "15")
                int bufferAfterMin,
        @ArraySchema(
                        arraySchema = @Schema(description = "Tour durations offered (minutes)."),
                        schema = @Schema(example = "60"))
                List<Integer> durationsOffered,
        @Schema(
                        description = "IANA timezone for scheduling.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone) {}
