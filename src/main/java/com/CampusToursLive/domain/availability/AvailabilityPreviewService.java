package com.CampusToursLive.domain.availability;

import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.OverrideMultiPreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.OverridePreviewResponse.DatePreview;
import com.CampusToursLive.web.dto.OverridePreviewResponse.TrimmedSegment;
import com.CampusToursLive.web.dto.ResolvedOccurrence;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide-facing date-specific override dry-run/PREVIEW (CTL-54 v2.1 Task 4): given a proposed
 * override that has NOT been saved, computes the resulting net-available windows per date exactly
 * as an actual save (see {@link AvailabilityWriteService#createException}) would produce them,
 * WITHOUT persisting anything — a pure read, so a guide/frontend can render a before/after.
 *
 * <p><b>Shares the write path's trim logic (DRY).</b> This calls the SAME package-visible {@link
 * AvailabilityWriteService#requireOverrideValid} guard (the same-day + 366-day-cap 422s) and the
 * SAME pure {@link AvailabilityWriteService#computeTrimmedSet} the real write uses — never a
 * re-implementation.
 *
 * <p><b>The full-list gotcha.</b> The real write path deliberately runs {@code computeTrimmedSet}
 * on only the OVERLAPPING subset of a date's existing exceptions (an identity/reason-preservation
 * optimization for the specific rows it deletes/reinserts) — non-overlapping siblings are left
 * physically untouched in the DB, so they never need to pass through the trim function at all. This
 * preview has no such optimization to make (it writes nothing), and the PROJECTION needs the
 * complete hypothetical exception set for the date to compute a correct net-available result —
 * including the untouched, non-overlapping siblings. So this class passes the date's FULL existing
 * -exception list into {@code computeTrimmedSet}; passing only the overlapping subset here would
 * silently drop the untouched siblings from the projection and produce a wrong preview.
 *
 * <p>Nothing is ever saved here: every {@link AvailabilityExceptionEntity} built for the projection
 * is a plain in-memory object, never passed to {@link AvailabilityExceptionRepository}.
 */
@Service
public class AvailabilityPreviewService {

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideBookingSettingsRepository settingsRepo;
    private final Clock clock;

    @Autowired
    public AvailabilityPreviewService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo) {
        this(rules, exceptions, settingsRepo, Clock.systemUTC());
    }

    /**
     * Test seam: inject a fixed {@link Clock} to pin "today" (and thus the materialization
     * horizon).
     */
    AvailabilityPreviewService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo,
            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.settingsRepo = settingsRepo;
        this.clock = clock;
    }

    /**
     * Whether {@code date} falls OUTSIDE the materialization horizon {@code [today, today+375)}
     * that {@link AvailabilityService#rematerialize} projects (CTL-54 #6) -- i.e. a past date or
     * one at or beyond {@code today + HORIZON_DAYS}. Such a date is "inert / not-yet-effective": an
     * actual save persists the override rows but no occurrence materializes for it yet, so the
     * preview must NOT claim net-available windows a save would not produce -- it reports empty
     * windows and flags the date inert instead, matching what {@code GET /availability} would show
     * post-save.
     */
    private boolean isInertDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        return date.isBefore(today)
                || !date.isBefore(today.plusDays(AvailabilityService.HORIZON_DAYS));
    }

    /**
     * Computes the per-date preview of applying the proposed override to {@code guideId}'s
     * availability over {@code [dateFrom, dateTo]} inclusive. Persists nothing. Runs the shared
     * {@link AvailabilityWriteService#requireOverrideValid} guard FIRST — before any {@link
     * IntervalMath.Span} is built — so a cross-midnight override or an over-366-day range 422s
     * exactly like {@code createException} would, even for a client hitting this endpoint directly.
     */
    @Transactional(readOnly = true)
    public OverridePreviewResponse preview(UUID guideId, OverridePreviewRequest req) {
        if (req == null) {
            throw new ValidationException("Request is required");
        }
        AvailabilityExceptionKind kind = parseKind(req.kind());
        LocalTime startLocal = parseLocalTime(req.startLocal());
        int windowMin = requireWindowMin(req.windowMin());
        LocalDate dateFrom = parseLocalDate(req.dateFrom(), "dateFrom");
        LocalDate dateTo = parseLocalDate(req.dateTo(), "dateTo");
        if (dateTo.isBefore(dateFrom)) {
            throw new ValidationException("dateTo must not be before dateFrom.");
        }

        AvailabilityWriteService.requireOverrideValid(startLocal, windowMin, dateFrom, dateTo);
        IntervalMath.Span newSpan = IntervalMath.spanOf(startLocal, windowMin);

        List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
        String guideTimezone = resolveGuideTimezone(guideId, guideRules);

        List<DatePreview> days = new ArrayList<>();
        long spanDays = ChronoUnit.DAYS.between(dateFrom, dateTo);
        for (long i = 0; i <= spanDays; i++) {
            LocalDate date = dateFrom.plusDays(i);
            if (isInertDate(date)) {
                days.add(inertPreview(date));
                continue;
            }
            days.add(previewForDate(guideId, date, guideRules, guideTimezone, kind, newSpan));
        }

        return new OverridePreviewResponse(days, true, null);
    }

    /**
     * Computes the per-date preview of applying a MULTI-WINDOW proposed override (CTL-54 v2.1 Task
     * 4, multi-window) to {@code guideId}'s availability over {@code [dateFrom, dateTo]} inclusive.
     * All windows of the same {@code kind} are applied TOGETHER and previewed as ONE combined
     * result per date, so the frontend never has to merge N single-window previews (which would
     * recompute overlap/trim/net itself). Persists nothing.
     *
     * <p>Every window is validated up front via the SAME {@link
     * AvailabilityWriteService#requireOverrideValid} guard the single-window {@link #preview} and
     * the real write use (same-day + 366-inclusive-date cap), BEFORE any {@link IntervalMath.Span}
     * is built. {@code windows} must be non-empty (else 422) UNLESS {@code replaceExisting} is
     * true, in which case an empty list is allowed and means "clear this kind for the day".
     *
     * <p><b>replaceExisting.</b> When absent/false (default) the windows are folded on top of the
     * date's FULL existing exceptions (unchanged behavior). When true, the date's same-kind
     * existing exceptions are DROPPED before folding (other-kind kept), so the dry-run shows the
     * day as if this kind were REPLACED by exactly {@code windows} -- making removals/edits (and
     * the empty = cleared case) render correctly.
     *
     * <p><b>Iterative trim/replace across the windows.</b> For each date the hypothetical set
     * starts as the seeded existing exceptions; then each window in order is folded in with the
     * SAME pure {@link AvailabilityWriteService#computeTrimmedSet} the real write uses, its result
     * fed forward as the input for the next window -- so newest-wins holds ACROSS the windows too
     * (a later window trims an earlier one), yielding the net non-overlapping hypothetical set with
     * ALL windows applied. That set is projected into net-available windows exactly as an actual
     * sequence of saves would produce.
     */
    @Transactional(readOnly = true)
    public OverridePreviewResponse previewMulti(UUID guideId, OverrideMultiPreviewRequest req) {
        if (req == null) {
            throw new ValidationException("Request is required");
        }
        AvailabilityExceptionKind kind = parseKind(req.kind());
        LocalDate dateFrom = parseLocalDate(req.dateFrom(), "dateFrom");
        LocalDate dateTo = parseLocalDate(req.dateTo(), "dateTo");
        // Window-INDEPENDENT range guard at entry (B7): the 366-day cap + dateTo >= dateFrom must
        // be
        // enforced BEFORE the empty-windows branch and the per-window loop -- otherwise a
        // replaceExisting=true request with empty windows skips the per-window requireOverrideValid
        // and the day-iteration loop below walks the full (unbounded) range, one DB read per date.
        AvailabilityWriteService.requireOverrideRange(dateFrom, dateTo);
        boolean replaceExisting = Boolean.TRUE.equals(req.replaceExisting());
        List<OverrideMultiPreviewRequest.Window> windows =
                req.windows() == null ? List.of() : req.windows();
        // Empty windows are allowed ONLY in replace mode (the "clear this kind for the day" case).
        // In the default on-top mode, windows must still be non-empty.
        if (windows.isEmpty() && !replaceExisting) {
            throw new ValidationException("windows must not be empty");
        }

        // Validate EVERY window that IS present (same-day + 366 cap) BEFORE building any Span, then
        // build all spans.
        List<IntervalMath.Span> spans = new ArrayList<>();
        for (OverrideMultiPreviewRequest.Window w : windows) {
            LocalTime startLocal = parseLocalTime(w.startLocal());
            int windowMin = requireWindowMin(w.windowMin());
            AvailabilityWriteService.requireOverrideValid(startLocal, windowMin, dateFrom, dateTo);
            spans.add(IntervalMath.spanOf(startLocal, windowMin));
        }

        List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
        String guideTimezone = resolveGuideTimezone(guideId, guideRules);

        List<DatePreview> days = new ArrayList<>();
        long spanDays = ChronoUnit.DAYS.between(dateFrom, dateTo);
        for (long i = 0; i <= spanDays; i++) {
            LocalDate date = dateFrom.plusDays(i);
            if (isInertDate(date)) {
                days.add(inertPreview(date));
                continue;
            }
            days.add(
                    previewMultiForDate(
                            guideId,
                            date,
                            guideRules,
                            guideTimezone,
                            kind,
                            spans,
                            replaceExisting));
        }

        return new OverridePreviewResponse(days, true, null);
    }

    /**
     * Builds one date's MULTI-WINDOW preview: loads the date's FULL existing-exception list once,
     * folds every window through the shared {@link AvailabilityWriteService#computeTrimmedSet} in
     * order (each window's result feeding the next -- newest-wins across the windows), projects the
     * final hypothetical set into net-available windows, and reports which existing exceptions ANY
     * of the windows would trim.
     */
    private DatePreview previewMultiForDate(
            UUID guideId,
            LocalDate date,
            List<GuideAvailabilityRuleEntity> guideRules,
            String guideTimezone,
            AvailabilityExceptionKind newKind,
            List<IntervalMath.Span> spans,
            boolean replaceExisting) {
        List<AvailabilityExceptionEntity> fullExisting =
                exceptions.findByGuideIdAndExceptionDate(guideId, date);

        List<AvailabilityWriteService.ExistingException> fullExistingPure =
                fullExisting.stream()
                        .map(
                                e ->
                                        new AvailabilityWriteService.ExistingException(
                                                e.getId(),
                                                e.getKind(),
                                                IntervalMath.spanOf(
                                                        e.getStartLocal(), e.getWindowMin())))
                        .toList();

        // Compute the hypothetical set via the SAME shared projection the real write uses (DRY).
        // Replace mode DROPS the same-kind existing (being replaced by exactly `spans`) and KEEPS
        // the other-kind existing, which the new windows then trim newest-wins -- identical to
        // AvailabilityWriteService.replaceOverrides, so preview and save can never diverge. Empty
        // `spans` in replace mode clears just this kind for the day. Default (on-top) mode folds
        // every window on top of ALL existing exceptions.
        List<AvailabilityWriteService.TrimmedException> hypotheticalSet =
                replaceExisting
                        ? AvailabilityWriteService.computeReplacedSet(
                                fullExistingPure, newKind, spans)
                        : AvailabilityWriteService.foldWindowsOverSeed(
                                fullExistingPure, newKind, spans);

        List<AvailabilityExceptionEntity> hypotheticalEntities =
                hypotheticalSet.stream().map(t -> transientException(guideId, date, t)).toList();

        AvailabilityHorizon oneDayHorizon = new AvailabilityHorizon(date, date.plusDays(1));
        ProjectionResult result =
                AvailabilityProjection.project(
                        guideRules, hypotheticalEntities, oneDayHorizon, guideTimezone);

        List<ResolvedOccurrence> resultingWindows =
                result.intervals().stream()
                        .map(iv -> new ResolvedOccurrence(iv.startAt(), iv.endAt()))
                        .toList();

        List<TrimmedSegment> trimmed =
                fullExisting.stream()
                        .filter(
                                e -> {
                                    IntervalMath.Span existingSpan =
                                            IntervalMath.spanOf(
                                                    e.getStartLocal(), e.getWindowMin());
                                    return spans.stream()
                                            .anyMatch(s -> IntervalMath.overlaps(existingSpan, s));
                                })
                        .map(
                                e ->
                                        new TrimmedSegment(
                                                e.getKind().name(),
                                                e.getStartLocal().toString(),
                                                e.getWindowMin()))
                        .toList();

        return new DatePreview(date.toString(), resultingWindows, trimmed, false);
    }

    /**
     * The preview entry for an out-of-horizon (inert) date (CTL-54 #6): empty net-available windows
     * and no trimmed segments, flagged {@code inert=true}. The date is not-yet-effective -- a save
     * persists its override rows but nothing materializes until the horizon reaches it.
     */
    private static DatePreview inertPreview(LocalDate date) {
        return new DatePreview(date.toString(), List.of(), List.of(), true);
    }

    /**
     * Builds one date's preview: loads the FULL existing-exception list for {@code date} (not just
     * the overlapping subset — see the class javadoc), runs it through the shared pure {@link
     * AvailabilityWriteService#computeTrimmedSet} to get the hypothetical non-overlapping set,
     * projects that hypothetical set (with the guide's real rules) into net-available windows, and
     * separately reports which of the date's existing exceptions the override overlaps (would
     * trim/clip on an actual save).
     */
    private DatePreview previewForDate(
            UUID guideId,
            LocalDate date,
            List<GuideAvailabilityRuleEntity> guideRules,
            String guideTimezone,
            AvailabilityExceptionKind newKind,
            IntervalMath.Span newSpan) {
        List<AvailabilityExceptionEntity> fullExisting =
                exceptions.findByGuideIdAndExceptionDate(guideId, date);

        List<AvailabilityWriteService.ExistingException> fullExistingPure =
                fullExisting.stream()
                        .map(
                                e ->
                                        new AvailabilityWriteService.ExistingException(
                                                e.getId(),
                                                e.getKind(),
                                                IntervalMath.spanOf(
                                                        e.getStartLocal(), e.getWindowMin())))
                        .toList();

        List<AvailabilityWriteService.TrimmedException> hypotheticalSet =
                AvailabilityWriteService.computeTrimmedSet(fullExistingPure, newKind, newSpan);

        List<AvailabilityExceptionEntity> hypotheticalEntities =
                hypotheticalSet.stream().map(t -> transientException(guideId, date, t)).toList();

        AvailabilityHorizon oneDayHorizon = new AvailabilityHorizon(date, date.plusDays(1));
        ProjectionResult result =
                AvailabilityProjection.project(
                        guideRules, hypotheticalEntities, oneDayHorizon, guideTimezone);

        List<ResolvedOccurrence> resultingWindows =
                result.intervals().stream()
                        .map(iv -> new ResolvedOccurrence(iv.startAt(), iv.endAt()))
                        .toList();

        List<TrimmedSegment> trimmed =
                fullExisting.stream()
                        .filter(
                                e ->
                                        IntervalMath.overlaps(
                                                IntervalMath.spanOf(
                                                        e.getStartLocal(), e.getWindowMin()),
                                                newSpan))
                        .map(
                                e ->
                                        new TrimmedSegment(
                                                e.getKind().name(),
                                                e.getStartLocal().toString(),
                                                e.getWindowMin()))
                        .toList();

        return new DatePreview(date.toString(), resultingWindows, trimmed, false);
    }

    /**
     * A plain, never-saved {@link AvailabilityExceptionEntity} that exists only to be fed into
     * {@link AvailabilityProjection#project} for this preview — it is never passed to {@link
     * AvailabilityExceptionRepository}.
     */
    private static AvailabilityExceptionEntity transientException(
            UUID guideId, LocalDate date, AvailabilityWriteService.TrimmedException t) {
        AvailabilityExceptionEntity e = new AvailabilityExceptionEntity();
        e.setId(UUID.randomUUID());
        e.setGuideId(guideId);
        e.setExceptionDate(date);
        e.setKind(t.kind());
        e.setStartLocal(LocalTime.ofSecondOfDay(t.span().startMin() * 60L));
        e.setWindowMin(t.span().endMin() - t.span().startMin());
        return e;
    }

    /**
     * Resolves a concrete exception zone for the guide, never throwing: settings row -> its zone;
     * else rules present -> the pure engine's MODE-of-rules heuristic (reused directly, see {@link
     * AvailabilityProjection#resolveExceptionTimezone}); else -> {@link
     * AvailabilityService#DEFAULT_TIMEZONE}. Mirrors {@link AvailabilityService}'s own resolution
     * order exactly, but is duplicated here (not extracted into a shared helper) — matching this
     * codebase's convention of keeping small per-feature reads independent of the write service's
     * tested surface (see {@link AvailabilityReadService}'s guideId-resolution javadoc for the same
     * rationale).
     */
    private String resolveGuideTimezone(
            UUID guideId, List<GuideAvailabilityRuleEntity> guideRules) {
        Optional<GuideBookingSettingsEntity> settings = settingsRepo.findByGuideId(guideId);
        if (settings.isPresent()) {
            return settings.get().getTimezone();
        }
        if (!guideRules.isEmpty()) {
            return AvailabilityProjection.resolveExceptionTimezone(guideRules);
        }
        return AvailabilityService.DEFAULT_TIMEZONE;
    }

    // ---------------------------------------------------------------------
    // Parsing helpers -- every one maps a bad value to a domain ValidationException (-> 422),
    // never a framework parse error. Mirrors AvailabilityWriteService's own parsing helpers.
    // ---------------------------------------------------------------------

    private static AvailabilityExceptionKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("kind is required");
        }
        try {
            return AvailabilityExceptionKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid kind (expected UNAVAILABLE or ADDITIONAL): " + raw);
        }
    }

    private static LocalTime parseLocalTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("startLocal is required");
        }
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid startLocal (expected e.g. \"09:00\"): " + raw);
        }
        // Reject sub-minute (seconds/nanos) precision (CTL-54 #7) so preview validation matches the
        // write path's whole-minute contract -- see AvailabilityWriteService.parseLocalTime.
        if (parsed.getSecond() != 0 || parsed.getNano() != 0) {
            throw new ValidationException(
                    "startLocal must be whole-minute precision (no seconds): " + raw);
        }
        return parsed;
    }

    private static int requireWindowMin(Integer windowMin) {
        if (windowMin == null || windowMin <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
        return windowMin;
    }

    private static LocalDate parseLocalDate(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(paramName + " is required");
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "Invalid " + paramName + " (expected e.g. \"2026-07-11\"): " + raw);
        }
    }
}
