package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body for {@code POST /availability/preview} (CTL-54 v2.1 Task 4, multi-window): a
 * proposed date-specific override composed of MULTIPLE time windows applied together over an
 * inclusive date range, previewed as ONE combined dry-run WITHOUT persisting anything. Same {@code
 * dateFrom}/{@code dateTo}/{@code kind} envelope as the single-window {@link
 * OverridePreviewRequest}, but carries a {@code windows} list instead of a single {@code
 * startLocal}/{@code windowMin} pair -- the backend applies every window together (newest-wins
 * trim/replace across the windows too) and returns the net result per date, so the frontend never
 * merges N single-window previews itself.
 *
 * <p>Bound as a JSON request body (a POST is the clean way to carry the {@code windows} array); the
 * single-window {@code GET /availability/preview} is kept unchanged alongside it.
 */
@Schema(
        name = "OverrideMultiPreviewRequest",
        description =
                "A proposed multi-window date-specific override (multiple time slots on one date or"
                        + " date range) previewed together as one combined dry-run, without saving"
                        + " anything.")
public record OverrideMultiPreviewRequest(
        @Schema(
                        description = "ISO-8601 inclusive start date of the previewed range.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String dateFrom,
        @Schema(
                        description = "ISO-8601 inclusive end date of the previewed range.",
                        example = "2026-07-12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String dateTo,
        @Schema(
                        description =
                                "UNAVAILABLE removes availability; ADDITIONAL adds availability."
                                        + " Applies to every window in this request.",
                        example = "ADDITIONAL",
                        allowableValues = {"UNAVAILABLE", "ADDITIONAL"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kind,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "The time windows to apply together on each date"
                                                        + " in the range. Must be non-empty UNLESS"
                                                        + " replaceExisting is true, in which case an"
                                                        + " empty list clears this kind for the day."
                                                        + " Later windows trim earlier overlapping"
                                                        + " ones (newest-wins).",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<Window> windows,
        @Schema(
                        description =
                                "When true, preview the day as if this kind's EXISTING exceptions"
                                        + " were REPLACED by exactly these windows (same-kind"
                                        + " existing dropped first, other-kind preserved) -- so"
                                        + " removals/edits render correctly and an empty windows"
                                        + " list clears this kind for the day. When false or absent"
                                        + " (default), the windows are applied ON TOP of ALL"
                                        + " existing exceptions (windows must then be non-empty).",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Boolean replaceExisting) {

    /**
     * Backward-compatible constructor for callers that predate {@code replaceExisting}: defaults it
     * to {@code false} (windows applied on top of ALL existing exceptions -- the original
     * behavior).
     */
    public OverrideMultiPreviewRequest(
            String dateFrom, String dateTo, String kind, List<Window> windows) {
        this(dateFrom, dateTo, kind, windows, false);
    }

    /**
     * One proposed time window (a wall-clock start plus a length in minutes) within a {@link
     * OverrideMultiPreviewRequest}. Same span shape as a single-window override; combined with the
     * enclosing request's {@code kind}.
     */
    @Schema(
            name = "OverrideMultiPreviewWindow",
            description =
                    "One proposed time window (wall-clock start + length in minutes) within a"
                            + " multi-window override preview.")
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
