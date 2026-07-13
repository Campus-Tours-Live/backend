package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AffectedBookingResponse;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsUpdateRequest;
import com.CampusToursLive.web.dto.OverrideReplaceRequest;
import com.CampusToursLive.web.dto.RulesReplaceRequest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide-facing availability WRITE API (CTL-54 Task 5): create/update/delete rules and exceptions,
 * plus read/update booking settings. This is deliberately a SEPARATE service from {@link
 * AvailabilityService} (which owns the pure "recompute + wholesale-replace occurrences" persistence
 * step, Task 3) — this class owns CRUD + validation + the guide-id / IDOR resolution, and calls
 * {@link AvailabilityService#rematerialize(UUID)} at the end of every write so the materialized
 * occurrences never lag the rules/exceptions/settings the guide just edited.
 *
 * <p><b>guideId</b> here is always {@code guide_profiles.id} (looked up from the authenticated
 * {@code UserEntity} via {@code guide_profiles.user_id}) — the same id {@code
 * guide_availability_rules.guide_id} / {@code availability_exceptions.guide_id} / {@code
 * guide_booking_settings.guide_id} reference, and the same resolution {@link
 * com.CampusToursLive.domain.tour.TourOfferingService} already uses for offerings.
 *
 * <p><b>IDOR safety.</b> Every by-id rule/exception lookup goes through {@code
 * findByIdAndGuideId(id, guideId)} — a row owned by a different guide is indistinguishable from a
 * nonexistent one (404), never leaked or mutated.
 *
 * <p><b>Settings auto-provisioning.</b> A rule/exception create first ensures the guide has a
 * settings row (inserting the V1 defaults if not), both so the new rule's timezone has a concrete
 * settings zone to copy and so {@link AvailabilityService#rematerialize} always has a settings tz
 * to resolve (closing the T3 "no settings row" edge at the source).
 *
 * <p><b>Write-time coalesce (CTL-54).</b> {@code createRule}/{@code updateRule} no longer reject a
 * sibling that is a same-day, same-effective-range, ACTIVE overlap or touch (e.g. Sunday {@code
 * 9:00-10:00} + {@code 10:00-10:45}) — they MERGE it into one stored rule instead. The coalesce
 * GROUP key is the identical tuple {@code (dayOfWeek, effectiveFrom, effectiveTo, active=true)}:
 * only rules sharing every one of those merge, because two rules with different effective ranges
 * have different validity windows and merging their spans would leak availability into dates one of
 * them never covered; inactive rules never merge (deactivate-preserve). A sibling whose effective
 * range is DIFFERENT but still overlaps the candidate's (e.g. two overlapping "seasons") still
 * conflicts — {@link #validateNoOverlap} keeps that guard. See {@link #coalesceActiveGroup(UUID,
 * short, LocalDate, LocalDate)} for the merge algorithm.
 */
@Service
public class AvailabilityWriteService {

    /** Matches the spec's global cap on any guide's {@code max_advance_days}. */
    static final int MAX_MAX_ADVANCE_DAYS = 365;

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideBookingSettingsRepository settingsRepo;
    private final GuideProfileRepository guides;
    private final AvailabilityService availabilityService;
    private final EntityManager entityManager;
    private final BookingRepository bookings;
    private final GuideAvailabilityOccurrenceRepository occurrences;
    private final Clock clock;

    @Autowired
    public AvailabilityWriteService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo,
            GuideProfileRepository guides,
            AvailabilityService availabilityService,
            EntityManager entityManager,
            BookingRepository bookings,
            GuideAvailabilityOccurrenceRepository occurrences) {
        this(
                rules,
                exceptions,
                settingsRepo,
                guides,
                availabilityService,
                entityManager,
                bookings,
                occurrences,
                Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to pin the default {@code effectiveFrom}. */
    AvailabilityWriteService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo,
            GuideProfileRepository guides,
            AvailabilityService availabilityService,
            EntityManager entityManager,
            BookingRepository bookings,
            GuideAvailabilityOccurrenceRepository occurrences,
            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.settingsRepo = settingsRepo;
        this.guides = guides;
        this.availabilityService = availabilityService;
        this.entityManager = entityManager;
        this.bookings = bookings;
        this.occurrences = occurrences;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // Rules.
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> listRules(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return rules.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toRuleResponse)
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse createRule(UserEntity user, AvailabilityRuleRequest req) {
        UUID guideId = requireGuideId(user);
        validateRuleInput(req);

        GuideBookingSettingsEntity settings = getOrCreateSettings(guideId);

        GuideAvailabilityRuleEntity r = new GuideAvailabilityRuleEntity();
        r.setId(UUID.randomUUID());
        r.setGuideId(guideId);
        r.setDayOfWeek(req.dayOfWeek().shortValue());
        r.setStartLocal(parseLocalTime(req.startLocal()));
        r.setWindowMin(req.windowMin());
        // timezone is server-set = the guide's settings timezone (read-only-tz invariant), never
        // the client's.
        r.setTimezone(settings.getTimezone());
        r.setEffectiveFrom(
                req.effectiveFrom() != null
                        ? parseLocalDate(req.effectiveFrom())
                        : LocalDate.now(clock));
        r.setEffectiveTo(req.effectiveTo() != null ? parseLocalDate(req.effectiveTo()) : null);
        r.setActive(req.active() == null || req.active());
        validateEffectiveRange(r.getEffectiveFrom(), r.getEffectiveTo());
        validateSameDay(r.getStartLocal(), r.getWindowMin());
        validateNoOverlap(
                guideId,
                null,
                r.getDayOfWeek(),
                r.getStartLocal(),
                r.getWindowMin(),
                r.getEffectiveFrom(),
                r.getEffectiveTo());

        rules.save(r);
        AvailabilityRuleResponse result = coalesceAndResolveResult(guideId, r);
        availabilityService.rematerialize(guideId);
        return result;
    }

    @Transactional
    public AvailabilityRuleResponse updateRule(
            UserEntity user, UUID id, AvailabilityRuleRequest req) {
        UUID guideId = requireGuideId(user);
        GuideAvailabilityRuleEntity r =
                rules.findByIdAndGuideId(id, guideId)
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));
        if (req == null) {
            throw new ValidationException("Request body is required");
        }

        // TRUE partial PATCH (unlike createRule's all-fields-required validateRuleInput):
        // dayOfWeek/startLocal/windowMin fall back to the stored rule `r`'s current values when
        // omitted from the request, exactly like effectiveFrom/effectiveTo/active already do below
        // -- this is what lets a client PATCH just `{ "active": false }` without resending the
        // whole rule.
        //
        // Resolve every candidate value into LOCALS first and validate BEFORE mutating the managed
        // entity -- `r` came from `findByIdAndGuideId`, so it is already attached to this
        // transaction's persistence context; a repository query inside validateNoOverlap triggers
        // Hibernate's auto-flush, which would silently persist a rejected update if the entity's
        // setters had already run.
        short dayOfWeek = req.dayOfWeek() != null ? req.dayOfWeek().shortValue() : r.getDayOfWeek();
        if (dayOfWeek < 0 || dayOfWeek > 6) {
            throw new ValidationException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
        }
        LocalTime startLocal =
                req.startLocal() != null ? parseLocalTime(req.startLocal()) : r.getStartLocal();
        int windowMin = req.windowMin() != null ? req.windowMin() : r.getWindowMin();
        if (windowMin <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
        // timezone is NEVER updated from the request — it stays = the guide's settings timezone.
        LocalDate effectiveFrom =
                req.effectiveFrom() != null
                        ? parseLocalDate(req.effectiveFrom())
                        : r.getEffectiveFrom();
        LocalDate effectiveTo =
                req.effectiveTo() != null ? parseLocalDate(req.effectiveTo()) : r.getEffectiveTo();
        validateEffectiveRange(effectiveFrom, effectiveTo);
        validateSameDay(startLocal, windowMin);
        validateNoOverlap(
                guideId, r.getId(), dayOfWeek, startLocal, windowMin, effectiveFrom, effectiveTo);

        r.setDayOfWeek(dayOfWeek);
        r.setStartLocal(startLocal);
        r.setWindowMin(windowMin);
        r.setEffectiveFrom(effectiveFrom);
        r.setEffectiveTo(effectiveTo);
        if (req.active() != null) {
            r.setActive(req.active());
        }

        rules.save(r);
        AvailabilityRuleResponse result = coalesceAndResolveResult(guideId, r);
        availabilityService.rematerialize(guideId);
        return result;
    }

    @Transactional
    public List<AvailabilityRuleResponse> deleteRule(UserEntity user, UUID id) {
        UUID guideId = requireGuideId(user);
        GuideAvailabilityRuleEntity r =
                rules.findByIdAndGuideId(id, guideId)
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));
        rules.delete(r);
        availabilityService.rematerialize(guideId);
        return rules.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toRuleResponse)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Exceptions.
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityExceptionResponse> listExceptions(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return exceptions.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toExceptionResponse)
                .toList();
    }

    /**
     * Creates a date-specific override (CTL-54 v2.1 Task 3): either a single {@code exceptionDate}
     * or a {@code dateFrom}/{@code dateTo} multi-day range, applied per-date. Every date's stored
     * same-date exceptions end up NON-OVERLAPPING (newest-wins trim/replace, symmetric across
     * {@code UNAVAILABLE}/{@code ADDITIONAL}) — see {@link #computeTrimmedSet}.
     *
     * <p><b>Auto-flush safety.</b> {@link #requireOverrideValid} runs, and every date's trim PLAN
     * is computed (read-only — no delete/insert), BEFORE any date's plan is applied. This class has
     * no Spring transactional proxy under {@code @DataJpaTest} (constructed with {@code new}), so a
     * rejected write must never leave partial state auto-flushed to the DB; validating and planning
     * everything upfront, across every date, before any mutation guarantees that.
     */
    @Transactional
    public AvailabilityExceptionResponse createException(
            UserEntity user, AvailabilityExceptionRequest req) {
        UUID guideId = requireGuideId(user);
        validateExceptionInput(req);

        AvailabilityExceptionKind kind = parseKind(req.kind());
        LocalTime startLocal = parseLocalTime(req.startLocal());
        int windowMin = req.windowMin();
        String reason = req.reason();
        LocalDate[] range = resolveDateRange(req);
        LocalDate dateFrom = range[0];
        LocalDate dateTo = range[1];

        requireOverrideValid(startLocal, windowMin, dateFrom, dateTo);
        IntervalMath.Span newSpan = IntervalMath.spanOf(startLocal, windowMin);

        // Ensure a settings row exists so rematerialize always has a settings tz to resolve, even
        // for a guide who only ever adds exceptions and never a rule.
        getOrCreateSettings(guideId);

        // Plan every date FIRST (read-only) -- see the auto-flush-safety note above -- then apply.
        List<DatePlan> plans = new ArrayList<>();
        long spanDays = ChronoUnit.DAYS.between(dateFrom, dateTo);
        for (long i = 0; i <= spanDays; i++) {
            plans.add(planOverrideForDate(guideId, dateFrom.plusDays(i), kind, newSpan, null));
        }

        AvailabilityExceptionEntity firstCreated = null;
        for (DatePlan plan : plans) {
            AvailabilityExceptionEntity created = applyPlan(plan, reason);
            if (firstCreated == null) {
                firstCreated = created;
            }
        }

        availabilityService.rematerialize(guideId);
        return toExceptionResponse(firstCreated);
    }

    /**
     * Edits an existing exception's time/kind/date (CTL-54 v2.1 Task 3): the old row is deleted and
     * the new override re-applied via the SAME {@link #computeTrimmedSet} trim used by {@link
     * #createException}, trimming overlapping SIBLINGS on the (possibly new) date while
     * SELF-EXCLUDING the edited row (it never trims against its own new span). Single-date only —
     * multi-day only applies to create.
     */
    @Transactional
    public AvailabilityExceptionResponse updateException(
            UserEntity user, UUID id, AvailabilityExceptionRequest req) {
        UUID guideId = requireGuideId(user);
        AvailabilityExceptionEntity e =
                exceptions
                        .findByIdAndGuideId(id, guideId)
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));
        validateExceptionInput(req);

        AvailabilityExceptionKind kind = parseKind(req.kind());
        LocalTime startLocal = parseLocalTime(req.startLocal());
        int windowMin = req.windowMin();
        String reason = req.reason();
        LocalDate date = resolveDateRange(req)[0];

        requireOverrideValid(startLocal, windowMin, date, date);
        IntervalMath.Span newSpan = IntervalMath.spanOf(startLocal, windowMin);

        // Plan BEFORE mutating anything (auto-flush safety, same as createException); the edited
        // row is self-excluded from its own trim via `excludeId`.
        DatePlan plan = planOverrideForDate(guideId, date, kind, newSpan, e.getId());
        exceptions.delete(e);
        AvailabilityExceptionEntity updated = applyPlan(plan, reason);

        availabilityService.rematerialize(guideId);
        return toExceptionResponse(updated);
    }

    @Transactional
    public List<AvailabilityExceptionResponse> deleteException(UserEntity user, UUID id) {
        UUID guideId = requireGuideId(user);
        AvailabilityExceptionEntity e =
                exceptions
                        .findByIdAndGuideId(id, guideId)
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));
        exceptions.delete(e);
        availabilityService.rematerialize(guideId);
        return exceptions.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toExceptionResponse)
                .toList();
    }

    /**
     * ATOMIC single-day override replace (CTL-54 v2.1 remediation B2): replaces the guide's
     * same-{@code kind} exceptions on {@code date} with exactly {@code windows}, in ONE transaction
     * under the per-guide advisory lock. An EMPTY {@code windows} list is allowed and CLEARS that
     * kind for the day; other-kind exceptions on the date are preserved (trimmed only where a new
     * window overlaps them, newest-wins). Returns the date's resulting stored exceptions.
     *
     * <p><b>Entry guard BEFORE any mutation (B2 data-loss guard).</b> The date range guard ({@link
     * #requireOverrideRange}) and every present window's same-day guard ({@link
     * #requireOverrideValid}) run FIRST, before a single row is deleted — so an invalid window
     * (e.g. one crossing midnight) throws a {@link ValidationException} while the prior override is
     * still fully intact, never after a partial delete.
     *
     * <p><b>One transaction under the advisory lock.</b> {@link #lockGuide} is taken before the
     * delete/insert, and {@link AvailabilityService#rematerialize} runs at Spring {@code REQUIRED}
     * propagation so it JOINS this transaction (it re-acquires the SAME per-guide xact-advisory
     * key, which is a harmless re-entrant no-op). Because delete + insert + rematerialize share one
     * transaction, ANY mid-transaction failure (a rematerialize error, a constraint trip) rolls the
     * WHOLE thing back — the prior override is never lost.
     *
     * <p><b>DRY.</b> The replaced set is computed by the SAME {@link #computeReplacedSet}
     * projection the {@code replaceExisting} dry-run preview uses, so preview and save can never
     * diverge.
     */
    @Transactional
    public List<AvailabilityExceptionResponse> replaceOverrides(
            UUID guideId, OverrideReplaceRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.kind() == null || req.kind().isBlank()) {
            throw new ValidationException("kind is required");
        }
        AvailabilityExceptionKind kind = parseKind(req.kind());
        if (req.date() == null || req.date().isBlank()) {
            throw new ValidationException("date is required");
        }
        LocalDate date = parseLocalDate(req.date());
        List<OverrideReplaceRequest.Window> windows =
                req.windows() == null ? List.of() : req.windows();

        // ENTRY GUARD, BEFORE ANY MUTATION: window-independent range guard + per-window same-day
        // guard. Building the spans here (before the lock/delete) guarantees a bad window throws
        // while the prior override is still fully intact.
        requireOverrideRange(date, date);
        List<IntervalMath.Span> spans = new ArrayList<>();
        for (OverrideReplaceRequest.Window w : windows) {
            LocalTime startLocal = parseLocalTime(w.startLocal());
            if (w.windowMin() == null || w.windowMin() <= 0) {
                throw new ValidationException("windowMin must be greater than 0");
            }
            requireOverrideValid(startLocal, w.windowMin(), date, date);
            spans.add(IntervalMath.spanOf(startLocal, w.windowMin()));
        }

        // Per-guide advisory lock (CTL-54 B5) — held across delete + insert + rematerialize, all in
        // this one transaction. rematerialize re-acquires the same xact-scoped key (re-entrant).
        lockGuide(guideId);

        // Ensure a settings row exists so rematerialize always has a settings tz to resolve.
        getOrCreateSettings(guideId);

        List<AvailabilityExceptionEntity> existingEntities =
                exceptions.findByGuideIdAndExceptionDate(guideId, date);
        List<ExistingException> existingPure =
                existingEntities.stream()
                        .map(
                                e ->
                                        new ExistingException(
                                                e.getId(),
                                                e.getKind(),
                                                IntervalMath.spanOf(
                                                        e.getStartLocal(), e.getWindowMin())))
                        .toList();

        // The desired final set for the date (shared projection with the preview).
        List<TrimmedException> desired = computeReplacedSet(existingPure, kind, spans);

        // Rows to delete: every same-kind row (being replaced) + every other-kind row a new window
        // overlaps (it gets trimmed). Non-overlapping other-kind rows are left PHYSICALLY untouched
        // (same id/reason), so `desired` still contains them unchanged — subtract those from the
        // insert set so they are neither deleted nor re-inserted.
        List<AvailabilityExceptionEntity> toDelete = new ArrayList<>();
        List<TrimmedException> keptUntouched = new ArrayList<>();
        for (AvailabilityExceptionEntity e : existingEntities) {
            IntervalMath.Span existingSpan =
                    IntervalMath.spanOf(e.getStartLocal(), e.getWindowMin());
            boolean sameKind = e.getKind() == kind;
            boolean overlapsWindow =
                    spans.stream().anyMatch(s -> IntervalMath.overlaps(existingSpan, s));
            if (sameKind || overlapsWindow) {
                toDelete.add(e);
            } else {
                keptUntouched.add(new TrimmedException(e.getKind(), existingSpan));
            }
        }

        List<TrimmedException> toInsert = new ArrayList<>(desired);
        for (TrimmedException kept : keptUntouched) {
            toInsert.remove(kept); // record equality on (kind, span); removes one match.
        }

        // Apply: delete replaced/trimmed rows, then insert the new windows + any trimmed remnants.
        toDelete.forEach(exceptions::delete);
        for (TrimmedException t : toInsert) {
            insertException(guideId, date, t.kind(), t.span(), null);
        }

        availabilityService.rematerialize(guideId);
        return exceptions.findByGuideIdAndExceptionDate(guideId, date).stream()
                .map(AvailabilityWriteService::toExceptionResponse)
                .toList();
    }

    /**
     * ATOMIC weekly-rule replace (CTL-54 v2.1 remediation B2, Task 5): replaces the guide's ACTIVE
     * recurring rules for {@code dayOfWeek} with exactly {@code windows}, in ONE transaction under
     * the per-guide advisory lock. This is the weekly counterpart to {@link #replaceOverrides} (the
     * single-day exception replace). An EMPTY {@code windows} list is allowed and CLEARS that
     * weekday's rules; every other weekday — and any INACTIVE (soft-deleted) rule on this weekday —
     * is left untouched. Every inserted rule takes the guide's settings timezone (the read-only-tz
     * invariant), an open-ended effective range starting today, and is active. Returns the
     * weekday's resulting ACTIVE rules.
     *
     * <p><b>Entry guard BEFORE any mutation (B2 data-loss guard).</b> Every present window's same-
     * day guard ({@link #validateSameDay}) runs FIRST, before a single row is deleted — so an
     * invalid window (one crossing midnight) throws a {@link ValidationException} while the prior
     * rules are still fully intact, never after a partial delete. A cross-midnight window is a
     * genuinely invalid span, NOT a self-overlap — coalescing cannot rescue it, so it still
     * rejects.
     *
     * <p><b>Self-overlap COALESCES, it is not rejected (CTL-54 accept-and-resolve).</b> Two request
     * windows that overlap OR touch (e.g. {@code 09:00-11:00} + {@code 10:00-12:00}, or {@code
     * 09:00-10:00} + {@code 10:00-11:00}) are accepted and MERGED into one stored rule, via the
     * SAME {@link #coalesceActiveGroup} sweep {@code createRule}/{@code updateRule} run — not the
     * old strict 422 reject. This makes every availability write path "accept-and-resolve" and
     * consistent: {@code createRule} already merges same-group overlap AND touch, {@code
     * replaceOverrides} (exceptions) accepts self-overlap and trims it (newest-wins), and the
     * occurrence projection layer coalesces to disjoint intervals regardless — so {@code
     * [09:00-11:00, 10:00-12:00]} and a single {@code [09:00-12:00]} yield IDENTICAL net
     * availability, and rejecting bought zero correctness, only friction. The "stored == exact
     * input" rationale the earlier reject claimed held nowhere else in this layer (create merges,
     * exceptions trim), so it was a local choice, not a system invariant. Concretely, the FE
     * DayHoursModal has only per-row {@code from < to} validation and NO cross-row overlap guard,
     * so it WILL send overlapping windows; the exceptions modal already accepts them silently,
     * while this weekly path used to 422 for the same harmless action — coalescing removes that
     * two-outcome inconsistency, and also fixes the older overlap-vs-touch split (overlap→422 but
     * touch→two rows).
     *
     * <p><b>One transaction under the advisory lock.</b> {@link #lockGuide} is taken before the
     * delete/insert, and {@link AvailabilityService#rematerialize} runs at Spring {@code REQUIRED}
     * propagation so it JOINS this transaction (re-acquiring the SAME per-guide xact-advisory key
     * is a harmless re-entrant no-op). Because delete + insert + rematerialize share one
     * transaction, ANY mid-transaction failure rolls the WHOLE thing back — the prior rules are
     * never lost.
     */
    @Transactional
    public List<AvailabilityRuleResponse> replaceRules(UUID guideId, RulesReplaceRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.dayOfWeek() == null || req.dayOfWeek() < 0 || req.dayOfWeek() > 6) {
            throw new ValidationException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
        }
        short dayOfWeek = req.dayOfWeek().shortValue();
        List<RulesReplaceRequest.Window> windows =
                req.windows() == null ? List.of() : req.windows();

        // ENTRY GUARD, BEFORE ANY MUTATION: per-window same-day guard (validateSameDay runs before
        // IntervalMath.spanOf, which would otherwise throw a raw IllegalArgumentException on a
        // cross-midnight window). Building the spans here (before the lock/delete) guarantees a
        // genuinely invalid (cross-midnight) window throws while the prior rules are still fully
        // intact. Self-overlap is NOT validated here — it is accepted and coalesced after insert.
        List<IntervalMath.Span> spans = new ArrayList<>();
        for (RulesReplaceRequest.Window w : windows) {
            LocalTime startLocal = parseLocalTime(w.startLocal());
            if (w.windowMin() == null || w.windowMin() <= 0) {
                throw new ValidationException("windowMin must be greater than 0");
            }
            validateSameDay(startLocal, w.windowMin());
            spans.add(IntervalMath.spanOf(startLocal, w.windowMin()));
        }

        // Per-guide advisory lock (CTL-54 B5) — held across delete + insert + rematerialize, all in
        // this one transaction. rematerialize re-acquires the same xact-scoped key (re-entrant).
        lockGuide(guideId);

        // Ensure a settings row exists so every inserted rule (and rematerialize) has a concrete
        // tz.
        GuideBookingSettingsEntity settings = getOrCreateSettings(guideId);

        // Delete the weekday's ACTIVE rules (inactive/soft-deleted rules on this weekday are left
        // untouched, matching validateNoOverlap's deactivate-preserve contract).
        rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, dayOfWeek).forEach(rules::delete);

        // Insert the new windows as active rules for this weekday.
        LocalDate effectiveFrom = LocalDate.now(clock);
        for (IntervalMath.Span span : spans) {
            GuideAvailabilityRuleEntity r = new GuideAvailabilityRuleEntity();
            r.setId(UUID.randomUUID());
            r.setGuideId(guideId);
            r.setDayOfWeek(dayOfWeek);
            r.setStartLocal(LocalTime.ofSecondOfDay(span.startMin() * 60L));
            r.setWindowMin(span.endMin() - span.startMin());
            // timezone is server-set = the guide's settings timezone (read-only-tz invariant).
            r.setTimezone(settings.getTimezone());
            r.setEffectiveFrom(effectiveFrom);
            r.setEffectiveTo(null);
            r.setActive(true);
            rules.save(r);
        }

        // Accept-and-resolve: merge any overlapping/touching windows the request sent into disjoint
        // maximal rules, reusing the SAME sweep createRule/updateRule run. All inserted rows share
        // one coalesce group (this weekday, effectiveFrom=today, effectiveTo=null, active), so a
        // single call collapses the whole request.
        coalesceActiveGroup(guideId, dayOfWeek, effectiveFrom, null);

        availabilityService.rematerialize(guideId);
        return rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, dayOfWeek).stream()
                .map(AvailabilityWriteService::toRuleResponse)
                .toList();
    }

    /**
     * Acquires the per-guide, TRANSACTION-scoped PostgreSQL advisory lock (CTL-54 B5) — the SAME
     * key derivation {@link AvailabilityService#rematerialize} and the horizon job use ({@code
     * hashtextextended(guideId::text, 0)}) — so the atomic replace's delete/insert are serialized
     * against a concurrent rematerialize for the same guide. Transaction-scoped: auto-releases on
     * commit/rollback, and re-acquiring the same key inside {@code rematerialize} later in this
     * same transaction is a harmless re-entrant no-op.
     */
    private void lockGuide(UUID guideId) {
        entityManager
                .createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:guideId AS text), 0))")
                .setParameter("guideId", guideId.toString())
                .getSingleResult();
    }

    // ---------------------------------------------------------------------
    // Settings.
    // ---------------------------------------------------------------------

    @Transactional
    public GuideBookingSettingsResponse getSettings(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return toSettingsResponse(getOrCreateSettings(guideId));
    }

    @Transactional
    public GuideBookingSettingsResponse updateSettings(
            UserEntity user, GuideBookingSettingsUpdateRequest req) {
        UUID guideId = requireGuideId(user);
        GuideBookingSettingsEntity settings = getOrCreateSettings(guideId);
        validateSettingsInput(req);

        String oldTimezone = settings.getTimezone();
        applyUpdates(settings, req);
        // save()/saveAndFlush() may return a DIFFERENT (merged) managed instance than the one
        // passed in -- reassign so the refresh below targets the actually-managed object.
        settings = settingsRepo.saveAndFlush(settings);
        // Refresh (not a repository re-query -- the entity is already identity-mapped in this
        // persistence context, so a plain re-query would just hand back the same in-memory
        // instance without pulling the DB-trigger-owned updated_at) so the response reflects the
        // real DB value.
        entityManager.refresh(settings);

        if (req.timezone() != null && !req.timezone().equals(oldTimezone)) {
            // Cascade invariant: every rule's timezone must equal the settings timezone.
            List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
            String newTimezone = settings.getTimezone();
            guideRules.forEach(r -> r.setTimezone(newTimezone));
            rules.saveAll(guideRules);
        }

        availabilityService.rematerialize(guideId);
        return toSettingsResponse(settings);
    }

    // ---------------------------------------------------------------------
    // Task 7 — "(A) allow + notify": detect (never block, never mutate) existing future CONFIRMED
    // bookings an availability edit left uncovered by any current occurrence.
    // ---------------------------------------------------------------------

    /**
     * The guide's own future CONFIRMED bookings not contained by any of their current materialized
     * occurrences, as of right after this call (so a caller invoking this AFTER a write's {@link
     * AvailabilityService#rematerialize} has committed sees the post-edit state). Never mutates a
     * booking — read-only.
     *
     * <p>Scope, per the spec: only {@code CONFIRMED} (the immutable, accepted state) and only
     * FUTURE (scheduled start at-or-after "now", via the injected {@link Clock} for testability).
     * PENDING bookings are still subject to guide acceptance/decline and re-validation is CTL-46's
     * job, not this warning; a booking that has already started/finished is moot to warn about.
     */
    @Transactional(readOnly = true)
    public List<AffectedBookingResponse> findFutureBookingsOutsideAvailability(UUID guideId) {
        Instant now = clock.instant();
        // Load the guide's materialized occurrences ONCE (a single query) and test containment in
        // memory, instead of one existsContaining round-trip PER future booking (CTL-54 #N+1). The
        // occurrences are already coalesced + disjoint and per-guide row counts are small, so the
        // in-memory scan is trivial. Containment mirrors existsContaining's SQL exactly: the
        // booking's SCHEDULED interval [schedStart, schedEnd) must sit fully inside some occurrence
        // [duringStart, duringEnd) -- half-open @> containment.
        List<GuideAvailabilityOccurrenceEntity> guideOccurrences =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId);
        return bookings
                .findByGuideIdAndStatusAndScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(
                        guideId, BookingStatus.CONFIRMED, now)
                .stream()
                .filter(b -> guideOccurrences.stream().noneMatch(o -> contains(o, b)))
                .map(AvailabilityWriteService::toAffectedBookingResponse)
                .toList();
    }

    /**
     * Whether {@code occurrence}'s half-open interval {@code [duringStart, duringEnd)} fully
     * contains the booking's scheduled half-open interval {@code [scheduledStart, scheduledEnd)} --
     * the in-memory equivalent of {@link GuideAvailabilityOccurrenceRepository#existsContaining}'s
     * {@code tstzrange @>} SQL: {@code duringStart <= scheduledStart && scheduledEnd <= duringEnd}.
     */
    private static boolean contains(GuideAvailabilityOccurrenceEntity occurrence, BookingEntity b) {
        return !occurrence.getDuringStartAt().isAfter(b.getScheduledStartAt())
                && !occurrence.getDuringEndAt().isBefore(b.getScheduledEndAt());
    }

    /**
     * Convenience overload for the controller: resolves the caller's {@code guideId} the same
     * IDOR-safe way every other method here does, then delegates to {@link
     * #findFutureBookingsOutsideAvailability(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<AffectedBookingResponse> findAffectedBookings(UserEntity user) {
        return findFutureBookingsOutsideAvailability(requireGuideId(user));
    }

    private static AffectedBookingResponse toAffectedBookingResponse(BookingEntity b) {
        return new AffectedBookingResponse(
                b.getId().toString(),
                b.getBookingNumber(),
                b.getScheduledStartAt().toString(),
                b.getScheduledEndAt().toString(),
                b.getStatus().name());
    }

    // ---------------------------------------------------------------------
    // Guide resolution + IDOR-safe settings provisioning.
    // ---------------------------------------------------------------------

    private UUID requireGuideId(UserEntity user) {
        GuideProfileEntity guide =
                guides.findByUserId(user.getId())
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "No guide profile — complete guide onboarding first"));
        return guide.getId();
    }

    /** Auto-provisions a default settings row for the guide if one does not already exist. */
    private GuideBookingSettingsEntity getOrCreateSettings(UUID guideId) {
        Optional<GuideBookingSettingsEntity> existing = settingsRepo.findByGuideId(guideId);
        if (existing.isPresent()) {
            return existing.get();
        }
        GuideBookingSettingsEntity created = new GuideBookingSettingsEntity();
        created.setGuideId(guideId);
        // save()/saveAndFlush() may return a DIFFERENT (merged) managed instance than the one
        // passed in (the guide_id PK is client-assigned, so Spring Data JPA's isNew() check may
        // route this through merge() rather than persist()) -- reassign so refresh() below targets
        // the actually-managed object, not the detached one we constructed.
        created = settingsRepo.saveAndFlush(created);
        // Refresh so updated_at (DB DEFAULT now(), insertable=false in the entity) is populated --
        // a repository re-query would return the same identity-mapped instance unchanged.
        entityManager.refresh(created);
        return created;
    }

    // ---------------------------------------------------------------------
    // Validation.
    // ---------------------------------------------------------------------

    private static void validateRuleInput(AvailabilityRuleRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.dayOfWeek() == null || req.dayOfWeek() < 0 || req.dayOfWeek() > 6) {
            throw new ValidationException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
        }
        if (req.startLocal() == null || req.startLocal().isBlank()) {
            throw new ValidationException("startLocal is required");
        }
        if (req.windowMin() == null || req.windowMin() <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
    }

    private static void validateEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ValidationException("effectiveTo must not be before effectiveFrom");
        }
    }

    /**
     * A single weekly rule range is always same-day (a cross-midnight range must be modeled as two
     * adjacent-day rows) — reject before any {@link IntervalMath.Span} is built so an out-of-range
     * value never reaches {@code IntervalMath} as a raw (and less friendly) {@link
     * IllegalArgumentException}. {@code windowMin} bringing the range to exactly {@code 1440}
     * (ending at midnight) is allowed.
     */
    private static void validateSameDay(LocalTime startLocal, int windowMin) {
        if (startLocal.toSecondOfDay() / 60 + windowMin > 1440) {
            throw new ValidationException("This time range cannot cross midnight.");
        }
    }

    /**
     * Rejects the candidate rule (day/time/effective-range) against the guide's other ACTIVE rules
     * on the same {@code dayOfWeek}, when {@code excludeId} is non-null the rule with that id is
     * self-excluded (the update-in-place case).
     *
     * <p><b>CTL-54 coalesce.</b> A sibling in the SAME coalesce group as the candidate — identical
     * {@code (effectiveFrom, effectiveTo)}, null-safe — is skipped here entirely: it is never
     * rejected for a time overlap/touch, because {@link #coalesceActiveGroup} merges it into the
     * candidate right after this candidate is saved instead. Two rules conflict here ONLY when BOTH
     * their time-of-day spans overlap (touching bounds do not) AND they are in DIFFERENT
     * effective-range groups that still overlap in time (e.g. two overlapping "seasons") — a
     * same-time rule with a disjoint effective range (e.g. a different, non-overlapping season)
     * never conflicts either way.
     */
    private void validateNoOverlap(
            UUID guideId,
            UUID excludeId,
            short dayOfWeek,
            LocalTime startLocal,
            int windowMin,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        IntervalMath.Span candidateSpan = IntervalMath.spanOf(startLocal, windowMin);
        List<GuideAvailabilityRuleEntity> siblings =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, dayOfWeek);
        for (GuideAvailabilityRuleEntity existing : siblings) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            if (isSameCoalesceGroup(effectiveFrom, effectiveTo, existing)) {
                continue;
            }
            IntervalMath.Span existingSpan =
                    IntervalMath.spanOf(existing.getStartLocal(), existing.getWindowMin());
            if (IntervalMath.overlaps(candidateSpan, existingSpan)
                    && effectiveRangesOverlap(
                            effectiveFrom,
                            effectiveTo,
                            existing.getEffectiveFrom(),
                            existing.getEffectiveTo())) {
                throw new ValidationException(
                        "This time range overlaps another range on " + dayLabel(dayOfWeek) + ".");
            }
        }
    }

    /**
     * Whether {@code existing} belongs to the same CTL-54 coalesce group as a candidate with the
     * given {@code effectiveFrom}/{@code effectiveTo} — i.e. identical {@code effectiveFrom} AND
     * null-safe-equal {@code effectiveTo}. {@code dayOfWeek} and {@code active=true} are already
     * fixed by the caller (every sibling here came from {@code
     * findByGuideIdAndDayOfWeekAndActiveTrue} for the candidate's own {@code dayOfWeek}), so this
     * method only needs to compare the effective-range half of the group key.
     */
    private static boolean isSameCoalesceGroup(
            LocalDate effectiveFrom, LocalDate effectiveTo, GuideAvailabilityRuleEntity existing) {
        return existing.getEffectiveFrom().equals(effectiveFrom)
                && Objects.equals(existing.getEffectiveTo(), effectiveTo);
    }

    /**
     * Loads a guide's ACTIVE rules for one {@code (dayOfWeek, effectiveFrom, effectiveTo)} coalesce
     * group, sorted by minute-of-day start — the read half of {@link #coalesceActiveGroup} and
     * {@link #findCoveringActiveRule}'s plan-then-apply split.
     */
    private List<GuideAvailabilityRuleEntity> loadActiveGroup(
            UUID guideId, short dayOfWeek, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, dayOfWeek).stream()
                .filter(r -> isSameCoalesceGroup(effectiveFrom, effectiveTo, r))
                .sorted(
                        Comparator.comparingInt(
                                r ->
                                        IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin())
                                                .startMin()))
                .toList();
    }

    /**
     * A computed-but-not-yet-applied merge for one maximal run of {@code >= 2} touching/
     * overlapping rules in a coalesce group: {@code keep} (the run's smallest-start rule, kept and
     * resized in place) gets a new {@code windowMin} spanning the run's union ({@code maxEnd -
     * keep.startMin}); every other rule in the run is deleted. A run of exactly 1 rule needs no
     * plan (nothing to merge) — see {@link #planMerge}, which returns {@code null} for that case.
     */
    private record RuleMergePlan(
            GuideAvailabilityRuleEntity keep,
            int windowMin,
            List<GuideAvailabilityRuleEntity> toDelete) {}

    /**
     * CTL-54 write-time coalesce: reduces a guide's ACTIVE rules sharing {@code (dayOfWeek,
     * effectiveFrom, effectiveTo)} into disjoint maximal weekly intervals, merging any run of rules
     * whose minute-of-day spans touch ({@code currStart == prevEnd}) or overlap. Called once at the
     * end of every rule create/update whose result is active — a deactivation never coalesces
     * (inactive rules never merge, see {@link #validateNoOverlap}'s deactivate-preserve contract).
     * Idempotent: this also sweeps up any PRE-EXISTING un-merged rows left in the group from before
     * this feature shipped, the next time the guide writes to that group — no data migration
     * needed.
     *
     * <p><b>Auto-flush safety.</b> Loads the ENTIRE group in ONE read (which auto-flushes the
     * candidate save that just ran, so the candidate is included), computes the full merge plan
     * from that in-memory snapshot with NO further repository queries, and only then applies
     * deletes/resizes — the same plan-then-apply discipline {@link #createException} already uses
     * via {@link #planOverrideForDate}, needed for the same reason documented on this class:
     * {@code @DataJpaTest} + {@code FlushModeType.AUTO} + this class's {@code new}-constructed (no
     * Spring transactional proxy) instantiation means any repository query auto-flushes pending
     * entity mutations, so interleaving reads and writes here could silently persist a half-applied
     * merge.
     */
    private void coalesceActiveGroup(
            UUID guideId, short dayOfWeek, LocalDate effectiveFrom, LocalDate effectiveTo) {
        List<GuideAvailabilityRuleEntity> group =
                loadActiveGroup(guideId, dayOfWeek, effectiveFrom, effectiveTo);

        // Plan (read-only, pure in-memory sweep — no repository calls below this point): a new
        // run starts whenever the next rule's start is strictly past the current run's end so far;
        // otherwise (touching or overlapping) it extends the current run.
        List<RuleMergePlan> plans = new ArrayList<>();
        List<GuideAvailabilityRuleEntity> run = new ArrayList<>();
        int runEnd = -1;
        for (GuideAvailabilityRuleEntity r : group) {
            int start = IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin()).startMin();
            int end = IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin()).endMin();
            if (!run.isEmpty() && start > runEnd) {
                plans.add(planMerge(run));
                run = new ArrayList<>();
            }
            run.add(r);
            runEnd = run.size() == 1 ? end : Math.max(runEnd, end);
        }
        if (!run.isEmpty()) {
            plans.add(planMerge(run));
        }

        // Apply.
        for (RuleMergePlan plan : plans) {
            if (plan == null) {
                continue; // a singleton run -- nothing to merge, left physically untouched.
            }
            plan.toDelete().forEach(rules::delete);
            plan.keep().setWindowMin(plan.windowMin());
            rules.save(plan.keep());
        }
    }

    /**
     * Builds the {@link RuleMergePlan} for one maximal run, or {@code null} for a singleton run.
     */
    private static RuleMergePlan planMerge(List<GuideAvailabilityRuleEntity> run) {
        if (run.size() < 2) {
            return null;
        }
        // `run` is a slice of `group`, which loadActiveGroup already sorted by startMin, so the
        // first element has the run's smallest start -- the deterministic rule kept in place.
        GuideAvailabilityRuleEntity keep = run.get(0);
        int minStart = IntervalMath.spanOf(keep.getStartLocal(), keep.getWindowMin()).startMin();
        int maxEnd = minStart;
        for (GuideAvailabilityRuleEntity r : run) {
            maxEnd =
                    Math.max(
                            maxEnd,
                            IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin()).endMin());
        }
        return new RuleMergePlan(keep, maxEnd - minStart, run.subList(1, run.size()));
    }

    /**
     * Re-finds, within a coalesce group, the ACTIVE rule whose span covers {@code
     * candidateStartMin} — after {@link #coalesceActiveGroup} runs, the row the caller just
     * created/updated may have been deleted and merged into a different (earlier-starting) row, so
     * the caller cannot simply re-read its own id.
     */
    private AvailabilityRuleResponse findCoveringActiveRule(
            UUID guideId,
            short dayOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int candidateStartMin) {
        return loadActiveGroup(guideId, dayOfWeek, effectiveFrom, effectiveTo).stream()
                .filter(
                        r -> {
                            IntervalMath.Span span =
                                    IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin());
                            return span.startMin() <= candidateStartMin
                                    && candidateStartMin < span.endMin();
                        })
                .findFirst()
                .map(AvailabilityWriteService::toRuleResponse)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "CTL-54 coalesce invariant violated: no active rule in the"
                                                + " group covers the candidate's own start minute"));
    }

    /**
     * Shared create/update tail (CTL-54): if the just-saved candidate {@code r} is ACTIVE, runs
     * {@link #coalesceActiveGroup} for its group and returns the (possibly different, merged) rule
     * whose span now covers {@code r}'s original start; if {@code r} is INACTIVE (a deactivation),
     * skips coalesce entirely (inactive rules never merge) and returns {@code r} itself unchanged.
     * Callers still run {@link AvailabilityService#rematerialize} once, AFTER this returns.
     */
    private AvailabilityRuleResponse coalesceAndResolveResult(
            UUID guideId, GuideAvailabilityRuleEntity r) {
        if (!r.isActive()) {
            return toRuleResponse(r);
        }
        int candidateStartMin = IntervalMath.spanOf(r.getStartLocal(), r.getWindowMin()).startMin();
        coalesceActiveGroup(guideId, r.getDayOfWeek(), r.getEffectiveFrom(), r.getEffectiveTo());
        return findCoveringActiveRule(
                guideId,
                r.getDayOfWeek(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                candidateStartMin);
    }

    /**
     * Whether {@code [aFrom, aTo]} and {@code [bFrom, bTo]} overlap, treating a {@code null} "to"
     * as open-ended ({@code +infinity}). {@code aFrom}/{@code bFrom} are never null (the entity
     * column is NOT NULL).
     */
    private static boolean effectiveRangesOverlap(
            LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsAtOrBeforeBEnd = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsAtOrBeforeAEnd = aTo == null || !bFrom.isAfter(aTo);
        return aStartsAtOrBeforeBEnd && bStartsAtOrBeforeAEnd;
    }

    private static final String[] DAY_OF_WEEK_LABELS = {
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    /** {@code 0} = Sunday .. {@code 6} = Saturday, matching {@code dayOfWeek}'s own convention. */
    private static String dayLabel(short dayOfWeek) {
        return DAY_OF_WEEK_LABELS[dayOfWeek];
    }

    private static void validateExceptionInput(AvailabilityExceptionRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        boolean hasRange = req.dateFrom() != null || req.dateTo() != null;
        if (!hasRange && (req.exceptionDate() == null || req.exceptionDate().isBlank())) {
            throw new ValidationException("exceptionDate is required");
        }
        if (req.kind() == null || req.kind().isBlank()) {
            throw new ValidationException("kind is required");
        }
        if (req.startLocal() == null || req.startLocal().isBlank()) {
            throw new ValidationException("startLocal is required");
        }
        if (req.windowMin() == null || req.windowMin() <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
    }

    /**
     * Resolves the request's single date OR multi-day range into {@code [dateFrom, dateTo]}
     * (inclusive, {@code dateFrom == dateTo} for a single date). {@code dateFrom}/{@code dateTo}
     * take precedence when both are present; a partial pair (only one of the two set) is rejected.
     */
    private static LocalDate[] resolveDateRange(AvailabilityExceptionRequest req) {
        if (req.dateFrom() != null || req.dateTo() != null) {
            if (req.dateFrom() == null || req.dateTo() == null) {
                throw new ValidationException(
                        "dateFrom and dateTo must both be provided for a multi-day override.");
            }
            LocalDate dateFrom = parseLocalDate(req.dateFrom());
            LocalDate dateTo = parseLocalDate(req.dateTo());
            if (dateTo.isBefore(dateFrom)) {
                throw new ValidationException("dateTo must not be before dateFrom.");
            }
            return new LocalDate[] {dateFrom, dateTo};
        }
        LocalDate date = parseLocalDate(req.exceptionDate());
        return new LocalDate[] {date, date};
    }

    /**
     * Shared entry guard (CTL-54 v2.1 Task 3) for create/edit exception AND (Task 4) the dry-run
     * preview — called at the very entry, BEFORE any {@link IntervalMath.Span} is constructed, so
     * an out-of-range value never reaches {@code IntervalMath} as a raw {@link
     * IllegalArgumentException}: (a) same-day — the override cannot cross midnight ({@code
     * windowMin} bringing it to exactly {@code 1440}, i.e. ending at midnight, is allowed); (b)
     * operation-size — the multi-day range is capped at, at most, 366 INCLUSIVE dates ({@code
     * dateTo - dateFrom + 1 <= 366}, i.e. one leap year's worth of dates counting both endpoints;
     * this bounds the write's row-count, NOT date-reach: a far-future override beyond the
     * materialization horizon is legal and stays inert until the roll-forward job reaches it).
     */
    static void requireOverrideValid(
            LocalTime startLocal, int windowMin, LocalDate dateFrom, LocalDate dateTo) {
        if (startLocal.toSecondOfDay() / 60 + windowMin > 1440) {
            throw new ValidationException("This time off cannot cross midnight.");
        }
        requireOverrideRange(dateFrom, dateTo);
    }

    /**
     * The WINDOW-INDEPENDENT half of {@link #requireOverrideValid} (CTL-54 v2.1 remediation B7/A3):
     * the operation-size cap only — {@code dateTo >= dateFrom} and the 366 INCLUSIVE-date range
     * ({@code dateTo - dateFrom + 1 <= 366}). Because it needs no window, it is callable at handler
     * ENTRY — before the empty-windows branch and before any per-window loop — so a huge range is
     * rejected up front even when there are ZERO windows to iterate (the case that let {@link
     * AvailabilityPreviewService#previewMulti} skip the per-window {@link #requireOverrideValid}
     * and fall through to a full-range day-iteration query storm). Present windows are still
     * validated for same-day via {@link #requireOverrideValid}.
     */
    static void requireOverrideRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new ValidationException("dateTo must not be before dateFrom.");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) + 1 > 366) {
            throw new ValidationException("Date range too large (max 366 days).");
        }
    }

    // ---------------------------------------------------------------------
    // Date-specific newest-wins trim/replace (CTL-54 v2.1 Task 3) -- shared with Task 4's dry-run.
    // ---------------------------------------------------------------------

    /**
     * One of a date's CURRENT exceptions, as pure data (id + kind + span) for {@link
     * #computeTrimmedSet}. The {@code id} lets a caller (here, {@link #planOverrideForDate})
     * self-exclude a specific row (the edited row, on update) from being trimmed against its own
     * new span; {@link #computeTrimmedSet} itself never looks at {@code id}.
     */
    record ExistingException(UUID id, AvailabilityExceptionKind kind, IntervalMath.Span span) {}

    /**
     * One row of a per-date newest-wins TRIMMED exception set — pure data (kind + span), no
     * entity/DB. {@link #computeTrimmedSet} ALWAYS appends the new override as the LAST element of
     * its returned list; every element before that is a remnant of one of the {@code existing}
     * entries that overlapped the new span, or an unchanged non-overlapping entry passed straight
     * through.
     */
    record TrimmedException(AvailabilityExceptionKind kind, IntervalMath.Span span) {}

    /**
     * PURE trim/replace primitive shared by the real write ({@link #createException}/{@link
     * #updateException} via {@link #planOverrideForDate}) and Task 4's dry-run preview: given a
     * date's current exceptions and a proposed new override, returns the new NON-OVERLAPPING
     * exception set for that date under "newest wins" — every existing exception whose span
     * overlaps {@code newSpan} is replaced by its {@link IntervalMath#subtract} remnants (same kind
     * as its source; empty remnants dropped); every non-overlapping existing exception passes
     * through unchanged; the new override ({@code newKind}/{@code newSpan}) is appended LAST. Pure
     * — no DB, no Spring; takes/returns plain data so Task 4 can reuse it against a hypothetical
     * set without writing anything.
     */
    static List<TrimmedException> computeTrimmedSet(
            List<ExistingException> existing,
            AvailabilityExceptionKind newKind,
            IntervalMath.Span newSpan) {
        List<TrimmedException> result = new ArrayList<>();
        for (ExistingException ex : existing) {
            if (IntervalMath.overlaps(ex.span(), newSpan)) {
                for (IntervalMath.Span remnant : IntervalMath.subtract(ex.span(), newSpan)) {
                    result.add(new TrimmedException(ex.kind(), remnant));
                }
            } else {
                result.add(new TrimmedException(ex.kind(), ex.span()));
            }
        }
        result.add(new TrimmedException(newKind, newSpan));
        return result;
    }

    /**
     * Folds {@code spans} (in order, newest-wins) on top of {@code seed} via the shared pure {@link
     * #computeTrimmedSet} — each window's trim/replace result becomes the input for the next, so a
     * later window trims an earlier one. Returns the net non-overlapping hypothetical set. Seeds
     * the result from {@code seed} so that with ZERO spans the seed passes straight through (the
     * "cleared kind, other-kind kept" case). Pure — no DB, no Spring.
     */
    static List<TrimmedException> foldWindowsOverSeed(
            List<ExistingException> seed,
            AvailabilityExceptionKind newKind,
            List<IntervalMath.Span> spans) {
        List<TrimmedException> result =
                seed.stream().map(e -> new TrimmedException(e.kind(), e.span())).toList();
        List<ExistingException> currentSet = seed;
        for (IntervalMath.Span span : spans) {
            result = computeTrimmedSet(currentSet, newKind, span);
            currentSet =
                    result.stream()
                            .map(t -> new ExistingException(UUID.randomUUID(), t.kind(), t.span()))
                            .toList();
        }
        return result;
    }

    /**
     * The SHARED "compute replaced set" projection (CTL-54 v2.1 remediation B2) used by BOTH the
     * {@code replaceExisting} dry-run preview ({@link AvailabilityPreviewService#previewMulti}) and
     * the atomic single-day save ({@link #replaceOverrides}) — so preview and save can never drift.
     * Replace semantics: DROP every same-kind ({@code newKind}) existing exception (they are being
     * replaced by exactly {@code spans}), KEEP the other-kind ones, then fold {@code spans} on top
     * newest-wins via {@link #foldWindowsOverSeed} (later windows trim earlier ones; new windows
     * trim overlapping other-kind siblings). With EMPTY {@code spans} the result is just the kept
     * other-kind exceptions — i.e. "clear this kind for the day". Pure — no DB, no Spring.
     */
    static List<TrimmedException> computeReplacedSet(
            List<ExistingException> existing,
            AvailabilityExceptionKind newKind,
            List<IntervalMath.Span> spans) {
        List<ExistingException> seed = existing.stream().filter(e -> e.kind() != newKind).toList();
        return foldWindowsOverSeed(seed, newKind, spans);
    }

    /**
     * A computed-but-not-yet-applied per-date write plan: the OVERLAPPING existing rows to delete
     * (siblings whose span intersects the new override; non-overlapping siblings are left
     * physically untouched — never deleted/reinserted) and the {@link #computeTrimmedSet} result to
     * insert in their place. Separating "plan" from "apply" is what lets {@link #createException}
     * compute every date's plan (read-only) before mutating any date (auto-flush safety).
     */
    private record DatePlan(
            UUID guideId,
            LocalDate date,
            List<AvailabilityExceptionEntity> toDelete,
            List<TrimmedException> toInsert) {}

    /**
     * Computes (without writing) the {@link DatePlan} for applying {@code newKind}/{@code newSpan}
     * to {@code date}: loads the date's current exceptions, self-excludes {@code excludeId} (the
     * edited row, on update — {@code null} on create), partitions the rest into overlapping (with
     * {@code newSpan}) vs. non-overlapping, and runs {@link #computeTrimmedSet} over ONLY the
     * overlapping subset (the non-overlapping ones are already the correct final state and are
     * simply left out of {@code toDelete} — re-trimming/reinserting them would be redundant and
     * would need to fabricate a lost {@code reason}).
     */
    private DatePlan planOverrideForDate(
            UUID guideId,
            LocalDate date,
            AvailabilityExceptionKind newKind,
            IntervalMath.Span newSpan,
            UUID excludeId) {
        List<AvailabilityExceptionEntity> existingEntities =
                exceptions.findByGuideIdAndExceptionDate(guideId, date);

        List<AvailabilityExceptionEntity> overlapping = new ArrayList<>();
        List<ExistingException> overlappingPure = new ArrayList<>();
        for (AvailabilityExceptionEntity ex : existingEntities) {
            if (excludeId != null && excludeId.equals(ex.getId())) {
                continue; // self-exclusion: never trim the edited row against its own new span.
            }
            IntervalMath.Span existingSpan =
                    IntervalMath.spanOf(ex.getStartLocal(), ex.getWindowMin());
            if (IntervalMath.overlaps(existingSpan, newSpan)) {
                overlapping.add(ex);
                overlappingPure.add(new ExistingException(ex.getId(), ex.getKind(), existingSpan));
            }
        }

        List<TrimmedException> trimmed = computeTrimmedSet(overlappingPure, newKind, newSpan);
        return new DatePlan(guideId, date, overlapping, trimmed);
    }

    /**
     * Applies a {@link DatePlan}: deletes the overlapped rows, then inserts every entry of {@code
     * toInsert} — the last of which is always the new override itself (see {@link
     * #computeTrimmedSet}), given {@code reason}; every earlier (remnant) entry gets a {@code null}
     * reason (a clipped remnant of a prior exception is not the same logical override anymore).
     * Returns the newly-inserted override entity (for the caller's response).
     */
    private AvailabilityExceptionEntity applyPlan(DatePlan plan, String reason) {
        plan.toDelete().forEach(exceptions::delete);

        List<TrimmedException> trimmed = plan.toInsert();
        AvailabilityExceptionEntity created = null;
        for (int i = 0; i < trimmed.size(); i++) {
            boolean isNewOverride = i == trimmed.size() - 1;
            AvailabilityExceptionEntity inserted =
                    insertException(
                            plan.guideId(),
                            plan.date(),
                            trimmed.get(i).kind(),
                            trimmed.get(i).span(),
                            isNewOverride ? reason : null);
            if (isNewOverride) {
                created = inserted;
            }
        }
        return created;
    }

    private AvailabilityExceptionEntity insertException(
            UUID guideId,
            LocalDate date,
            AvailabilityExceptionKind kind,
            IntervalMath.Span span,
            String reason) {
        AvailabilityExceptionEntity e = new AvailabilityExceptionEntity();
        e.setId(UUID.randomUUID());
        e.setGuideId(guideId);
        e.setExceptionDate(date);
        e.setKind(kind);
        e.setStartLocal(LocalTime.ofSecondOfDay(span.startMin() * 60L));
        e.setWindowMin(span.endMin() - span.startMin());
        e.setReason(reason);
        exceptions.save(e);
        return e;
    }

    private static void validateSettingsInput(GuideBookingSettingsUpdateRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.acceptanceMode() != null) {
            parseAcceptanceMode(req.acceptanceMode());
        }
        if (req.responseDeadlineMin() != null && req.responseDeadlineMin() <= 0) {
            throw new ValidationException("responseDeadlineMin must be greater than 0");
        }
        if (req.minNoticeMin() != null && req.minNoticeMin() < 0) {
            throw new ValidationException("minNoticeMin must not be negative");
        }
        if (req.maxAdvanceDays() != null
                && (req.maxAdvanceDays() <= 0 || req.maxAdvanceDays() > MAX_MAX_ADVANCE_DAYS)) {
            throw new ValidationException(
                    "maxAdvanceDays must be between 1 and " + MAX_MAX_ADVANCE_DAYS);
        }
        if (req.bufferBeforeMin() != null && req.bufferBeforeMin() < 0) {
            throw new ValidationException("bufferBeforeMin must not be negative");
        }
        if (req.bufferAfterMin() != null && req.bufferAfterMin() < 0) {
            throw new ValidationException("bufferAfterMin must not be negative");
        }
        if (req.durationsOffered() != null) {
            if (req.durationsOffered().isEmpty()) {
                throw new ValidationException("durationsOffered must not be empty");
            }
            if (req.durationsOffered().stream().anyMatch(d -> d == null || d <= 0)) {
                throw new ValidationException("durationsOffered entries must be greater than 0");
            }
        }
        if (req.timezone() != null) {
            parseZoneId(req.timezone());
        }
    }

    private static void applyUpdates(
            GuideBookingSettingsEntity settings, GuideBookingSettingsUpdateRequest req) {
        if (req.acceptanceMode() != null) {
            settings.setAcceptanceMode(parseAcceptanceMode(req.acceptanceMode()));
        }
        if (req.responseDeadlineMin() != null) {
            settings.setResponseDeadlineMin(req.responseDeadlineMin());
        }
        if (req.minNoticeMin() != null) {
            settings.setMinNoticeMin(req.minNoticeMin());
        }
        if (req.maxAdvanceDays() != null) {
            settings.setMaxAdvanceDays(req.maxAdvanceDays());
        }
        if (req.bufferBeforeMin() != null) {
            settings.setBufferBeforeMin(req.bufferBeforeMin());
        }
        if (req.bufferAfterMin() != null) {
            settings.setBufferAfterMin(req.bufferAfterMin());
        }
        if (req.durationsOffered() != null) {
            settings.setDurationsOffered(req.durationsOffered());
        }
        if (req.timezone() != null) {
            settings.setTimezone(req.timezone());
        }
    }

    // ---------------------------------------------------------------------
    // Parsing helpers — every one maps a bad value to a domain ValidationException (-> 422), never
    // a framework parse error.
    // ---------------------------------------------------------------------

    private static LocalTime parseLocalTime(String raw) {
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid startLocal (expected e.g. \"09:00\"): " + raw);
        }
        // Reject sub-minute (seconds/nanos) precision (CTL-54 #7): validation truncates to the
        // minute (toSecondOfDay()/60) but AvailabilityProjection.resolveWindow materializes the
        // FULL-precision LocalTime, so a value like "23:30:40" + windowMin=30 would pass the
        // same-day check (23:30 + 30 = 24:00) yet materialize to 00:00:40 the NEXT day. Rejecting
        // seconds up front keeps validation and materialization in agreement.
        if (parsed.getSecond() != 0 || parsed.getNano() != 0) {
            throw new ValidationException(
                    "startLocal must be whole-minute precision (no seconds): " + raw);
        }
        return parsed;
    }

    private static LocalDate parseLocalDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid date (expected e.g. \"2026-07-11\"): " + raw);
        }
    }

    private static AvailabilityExceptionKind parseKind(String raw) {
        try {
            return AvailabilityExceptionKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid kind (expected UNAVAILABLE or ADDITIONAL): " + raw);
        }
    }

    private static AcceptanceMode parseAcceptanceMode(String raw) {
        try {
            return AcceptanceMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid acceptanceMode (expected AUTO or MANUAL): " + raw);
        }
    }

    private static ZoneId parseZoneId(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException ex) {
            throw new ValidationException("Invalid IANA timezone: " + raw);
        }
    }

    // ---------------------------------------------------------------------
    // Response mapping.
    // ---------------------------------------------------------------------

    private static AvailabilityRuleResponse toRuleResponse(GuideAvailabilityRuleEntity r) {
        return new AvailabilityRuleResponse(
                r.getId().toString(),
                r.getDayOfWeek(),
                r.getStartLocal().toString(),
                r.getWindowMin(),
                r.getTimezone(),
                r.getEffectiveFrom() != null ? r.getEffectiveFrom().toString() : null,
                r.getEffectiveTo() != null ? r.getEffectiveTo().toString() : null,
                r.isActive());
    }

    private static AvailabilityExceptionResponse toExceptionResponse(
            AvailabilityExceptionEntity e) {
        return new AvailabilityExceptionResponse(
                e.getId().toString(),
                e.getExceptionDate().toString(),
                e.getKind().name(),
                e.getStartLocal().toString(),
                e.getWindowMin(),
                e.getReason());
    }

    private static GuideBookingSettingsResponse toSettingsResponse(GuideBookingSettingsEntity s) {
        return new GuideBookingSettingsResponse(
                s.getGuideId().toString(),
                s.getAcceptanceMode().name(),
                s.getResponseDeadlineMin(),
                s.getMinNoticeMin(),
                s.getMaxAdvanceDays(),
                s.getBufferBeforeMin(),
                s.getBufferAfterMin(),
                s.getDurationsOffered(),
                s.getTimezone(),
                s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
    }
}
