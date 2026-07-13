package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response for {@code GET /availability/preview} (CTL-54 v2.1 Task 4): the resulting net-available
 * windows the proposed override would produce, per date in the requested range, WITHOUT persisting
 * anything. {@code valid} is always {@code true} under the newest-wins trim/replace model (there is
 * no hard-block case for a date-specific override) -- the field (and {@code message}) are kept for
 * a future rule that might reject rather than trim.
 */
@Schema(
        name = "OverridePreviewResponse",
        description =
                "The resulting net-available windows the proposed override would produce, per"
                        + " date, without persisting anything.")
public record OverridePreviewResponse(
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "One entry per date in the previewed range.",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<DatePreview> days,
        @Schema(
                        description =
                                "Always true under the newest-wins trim/replace model; kept for a"
                                        + " future rule that might reject rather than trim.",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean valid,
        @Schema(
                        description =
                                "Free-text detail when valid is false; null under the current"
                                        + " trim/replace model.",
                        example = "null",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String message) {

    /**
     * One date's preview: the resulting net-available windows (as an actual save would produce
     * them) and which of that date's existing exception segments the override trims/clips.
     */
    @Schema(
            name = "DatePreview",
            description =
                    "One previewed date's resulting net-available windows and the existing"
                            + " exception segments the override would trim.")
    public record DatePreview(
            @Schema(
                            description = "ISO-8601 date this preview entry applies to.",
                            example = "2026-07-12",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String date,
            @ArraySchema(
                            arraySchema =
                                    @Schema(
                                            description =
                                                    "Resulting net-available occurrences for this"
                                                            + " date, exactly as an actual save would"
                                                            + " produce them.",
                                            requiredMode = Schema.RequiredMode.REQUIRED))
                    List<ResolvedOccurrence> resultingWindows,
            @ArraySchema(
                            arraySchema =
                                    @Schema(
                                            description =
                                                    "Existing exception segments on this date the"
                                                            + " override would trim/clip on an actual"
                                                            + " save.",
                                            requiredMode = Schema.RequiredMode.REQUIRED))
                    List<TrimmedSegment> trimmed,
            @Schema(
                            description =
                                    "True when this date is OUTSIDE the materialization horizon"
                                            + " [today, today+375) -- a past date or one beyond the"
                                            + " rolling horizon. A save would still persist the"
                                            + " override rows, but no occurrence materializes yet, so"
                                            + " resultingWindows is empty and the date is"
                                            + " not-yet-effective (it activates once the horizon"
                                            + " rolls forward). False for effective, in-horizon"
                                            + " dates.",
                            example = "false",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean inert) {}

    /**
     * One existing exception on the date whose span overlaps the proposed override (and would
     * therefore be clipped/removed by the newest-wins trim on an actual save).
     */
    @Schema(
            name = "TrimmedSegment",
            description =
                    "An existing exception segment the proposed override would trim/clip on an"
                            + " actual save.")
    public record TrimmedSegment(
            @Schema(
                            description = "Kind of the existing exception being trimmed.",
                            example = "ADDITIONAL",
                            allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String kind,
            @Schema(
                            description = "Wall-clock start time of the existing exception.",
                            example = "09:00",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String startLocal,
            @Schema(
                            description = "Window length in minutes of the existing exception.",
                            example = "60",
                            minimum = "1",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer windowMin) {}
}
