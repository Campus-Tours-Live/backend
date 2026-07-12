package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * Response for {@code GET /availability/preview} (CTL-54 v2.1 Task 4): the resulting net-available
 * windows the proposed override would produce, per date in the requested range, WITHOUT persisting
 * anything. {@code valid} is always {@code true} under the newest-wins trim/replace model (there is
 * no hard-block case for a date-specific override) — the field (and {@code message}) are kept for a
 * future rule that might reject rather than trim.
 *
 * <p>No springdoc yet (CTL-54 v2.1 Task 5 adds it).
 */
public record OverridePreviewResponse(List<DatePreview> days, boolean valid, String message) {

    /**
     * One date's preview: the resulting net-available windows (as an actual save would produce
     * them) and which of that date's existing exception segments the override trims/clips.
     */
    public record DatePreview(
            String date, List<ResolvedOccurrence> resultingWindows, List<TrimmedSegment> trimmed) {}

    /**
     * One existing exception on the date whose span overlaps the proposed override (and would
     * therefore be clipped/removed by the newest-wins trim on an actual save).
     */
    public record TrimmedSegment(String kind, String startLocal, Integer windowMin) {}
}
