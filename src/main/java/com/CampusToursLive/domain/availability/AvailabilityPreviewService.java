package com.CampusToursLive.domain.availability;

import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.OverridePreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.OverridePreviewResponse.DatePreview;
import com.CampusToursLive.web.dto.OverridePreviewResponse.TrimmedSegment;
import com.CampusToursLive.web.dto.ResolvedOccurrence;
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

    @Autowired
    public AvailabilityPreviewService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.settingsRepo = settingsRepo;
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
            days.add(previewForDate(guideId, date, guideRules, guideTimezone, kind, newSpan));
        }

        return new OverridePreviewResponse(days, true, null);
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

        return new DatePreview(date.toString(), resultingWindows, trimmed);
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
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid startLocal (expected e.g. \"09:00\"): " + raw);
        }
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
