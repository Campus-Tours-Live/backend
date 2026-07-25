package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response envelope for {@code AvailabilityController}'s WRITE endpoints only (rule/exception
 * create-update-delete, settings update): {@code { data, affectedBookings, meta } }.
 *
 * <p><b>Why not {@link ApiEnvelope} / {@link Meta}?</b> Both are shared, generic infrastructure
 * reused by every controller in the app (bookings, tours, universities, …) with no per-response
 * extensibility (no arbitrary-field map) — adding a booking-domain {@code affectedBookings} field
 * there would leak onto every unrelated response as a stray {@code null}. This is instead a SMALL,
 * dedicated wrapper scoped to the seven availability-write routes.
 *
 * <p><b>The {@code data} contract is untouched.</b> {@code data} carries exactly the same payload
 * the write endpoint already returned (e.g. {@code AvailabilityRuleResponse}, or the remaining-list
 * on delete) — {@code affectedBookings} is a NEW sibling field, not a rewrap of {@code data}, so
 * any existing consumer reading {@code $.data.*} is unaffected; a consumer that also wants the
 * warning reads the new {@code $.affectedBookings} array allow + notify" — the write always
 * succeeds and the flagged booking is never mutated, this is advisory-only for the guide UI).
 */
@Schema(
        name = "AvailabilityWriteResponse",
        description =
                "Success envelope for availability write endpoints: { data, affectedBookings,"
                        + " meta }. data is the write's normal payload, unaffected by"
                        + " affectedBookings.")
public record AvailabilityWriteResponse<T>(
        @Schema(
                        description =
                                "The write's normal payload — e.g. the created/updated rule or"
                                        + " exception, or the remaining list on delete.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                T data,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "The caller's own future CONFIRMED bookings this"
                                                        + " edit left uncovered by any current"
                                                        + " occurrence; empty when none. Advisory"
                                                        + " only — every listed booking is"
                                                        + " unchanged.",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<AffectedBookingResponse> affectedBookings,
        @Schema(
                        description = "Response metadata (request id + server timestamp).",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Meta meta) {
    public static <T> AvailabilityWriteResponse<T> of(
            T data, List<AffectedBookingResponse> affectedBookings) {
        return new AvailabilityWriteResponse<>(data, affectedBookings, Meta.now());
    }
}
