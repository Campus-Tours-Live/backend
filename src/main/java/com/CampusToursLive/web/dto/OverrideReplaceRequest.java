package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body for {@code POST /availability/overrides/replace} (CTL-54 v2.1 remediation B2): an
 * ATOMIC single-day replace of ONE kind's date-specific overrides. The guide's existing same-kind
 * exceptions for {@code date} are dropped and replaced by exactly {@code windows} in one
 * transaction; other-kind exceptions on that date are preserved (trimmed only where a new window
 * overlaps them, newest-wins). An EMPTY {@code windows} list is allowed and means "clear this kind
 * for the day".
 *
 * <p>Unlike {@link AvailabilityExceptionRequest} (which carries a single {@code startLocal}/{@code
 * windowMin} pair and an optional multi-day range) this is a single {@code date} plus a {@code
 * windows} list — there is deliberately NO date-range field (per the spec, a range replace cannot
 * be expressed here, so no over-366-day range can be smuggled in). Bound as a JSON body; a POST is
 * the clean way to carry the {@code windows} array.
 */
@Schema(
        name = "OverrideReplaceRequest",
        description =
                "An atomic single-day replace of one kind's date-specific overrides: the guide's"
                        + " existing same-kind exceptions for the date are replaced by exactly these"
                        + " windows in one transaction (empty windows clears that kind for the day);"
                        + " other-kind exceptions are preserved.")
public record OverrideReplaceRequest(
        @Schema(
                        description = "ISO-8601 date whose same-kind overrides are being replaced.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String date,
        @Schema(
                        description =
                                "Which kind of override to replace on this date. UNAVAILABLE removes"
                                        + " availability; ADDITIONAL adds it. Only this kind's"
                                        + " existing exceptions for the date are replaced.",
                        example = "UNAVAILABLE",
                        allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kind,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "The time windows this kind should have on the date"
                                                        + " after the replace. MAY be empty — an"
                                                        + " empty list clears this kind for the day."
                                                        + " Later windows trim earlier overlapping"
                                                        + " ones (newest-wins).",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<Window> windows) {

    /**
     * One time window (a wall-clock start plus a length in minutes) within a {@link
     * OverrideReplaceRequest}. Same span shape as every other date-specific override window.
     */
    @Schema(
            name = "OverrideReplaceWindow",
            description =
                    "One proposed time window (wall-clock start + length in minutes) within a"
                            + " single-day override replace.")
    public record Window(
            @Schema(
                            description = "Wall-clock start time in the guide's settings timezone.",
                            example = "09:00",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String startLocal,
            @Schema(
                            description = "Window length in minutes.",
                            example = "60",
                            minimum = "1",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer windowMin) {}
}
