package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** PATCH /guide/availability/booking-settings — all fields optional. */
@Schema(
        name = "UpdateBookingSettingsRequest",
        description = "Partial update for per-guide booking policy.")
public record UpdateBookingSettingsRequest(
        @Schema(
                        description = "How incoming bookings are accepted.",
                        example = "AUTO",
                        allowableValues = {"AUTO", "MANUAL"})
                String acceptanceMode,
        @Schema(
                        description = "Minutes the guide has to respond to a booking request.",
                        example = "120")
                Integer responseDeadlineMin,
        @Schema(
                        description = "Minimum notice before a slot can be booked (minutes).",
                        example = "720")
                Integer minNoticeMin,
        @Schema(description = "How far ahead participants may book (days).", example = "45")
                Integer maxAdvanceDays,
        @Schema(description = "Buffer before each tour (minutes).", example = "10")
                Integer bufferBeforeMin,
        @Schema(description = "Buffer after each tour (minutes).", example = "20")
                Integer bufferAfterMin,
        @ArraySchema(
                        arraySchema = @Schema(description = "Tour durations offered (minutes)."),
                        schema = @Schema(example = "60"))
                List<Integer> durationsOffered,
        @Schema(description = "IANA timezone for scheduling.", example = "America/New_York")
                String timezone) {}
