package com.CampusToursLive.domain.availability;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Availability persistence layer: re-derives a guide's materialized availability occurrences (and
 * DST notices) from their rules + exceptions over the rolling horizon and WHOLESALE-REPLACES them
 * in one transaction. The pure math lives in {@link AvailabilityProjection} (Task 2); this class is
 * only the "fetch inputs, project, replace outputs" wiring.
 *
 * <p><b>Wholesale replace, not incremental.</b> Occurrences and DST notices are a DERIVED cache
 * (spec "(G)"): {@link #rematerialize(UUID)} deletes the guide's existing rows and re-inserts the
 * freshly projected set, so the cache always exactly reflects the current rules/exceptions. It
 * never touches bookings (a separate table) — re-derivation therefore cannot orphan a confirmed
 * booking.
 *
 * <p>Task 5's rule/exception writes call {@link #rematerialize(UUID)} after every change; Task 4's
 * scheduled job calls it to roll the horizon forward (and reuses {@link #HORIZON_DAYS}).
 */
@Service
public class AvailabilityService {

    /**
     * Rolling horizon length in days: the global cap {@code MAX_MAX_ADVANCE_DAYS} (365) plus a
     * 10-day buffer, so the materialized set always covers at least as far out as any guide's
     * {@code max_advance_days} and Task 6's booking-containment check never false-rejects a
     * far-future booking. Task 4's roll-forward job reuses this constant.
     */
    public static final int HORIZON_DAYS = 375;

    /**
     * Fallback exception zone when a guide has exceptions but neither a settings row nor any rule
     * to infer a zone from. Matches the {@code guide_booking_settings.timezone} column DEFAULT in
     * V1__schema.sql ({@code 'America/Los_Angeles'}) — so a guide with no explicit settings
     * resolves to the same zone the DB would have defaulted them to. Referenced as a named
     * constant, never scattered as a literal.
     */
    public static final String DEFAULT_TIMEZONE = "America/Los_Angeles";

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideAvailabilityOccurrenceRepository occurrences;
    private final GuideAvailabilityDstNoticeRepository dstNotices;
    private final GuideBookingSettingsRepository settings;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * Production constructor — a system-UTC clock (no {@code Clock} bean needed in the context).
     */
    @Autowired
    public AvailabilityService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideAvailabilityOccurrenceRepository occurrences,
            GuideAvailabilityDstNoticeRepository dstNotices,
            GuideBookingSettingsRepository settings,
            EntityManager entityManager) {
        this(
                rules,
                exceptions,
                occurrences,
                dstNotices,
                settings,
                entityManager,
                Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to pin "today" (and thus the horizon). */
    AvailabilityService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideAvailabilityOccurrenceRepository occurrences,
            GuideAvailabilityDstNoticeRepository dstNotices,
            GuideBookingSettingsRepository settings,
            EntityManager entityManager,
            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.occurrences = occurrences;
        this.dstNotices = dstNotices;
        this.settings = settings;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Re-derives and wholesale-replaces the guide's materialized occurrences and DST notices over
     * the rolling {@link #HORIZON_DAYS} horizon, in one transaction.
     */
    @Transactional
    public void rematerialize(UUID guideId) {
        // CTL-54 B5: take the per-guide advisory lock BEFORE reading inputs or touching the derived
        // cache, so two concurrent rematerializes for this guide (a guide-facing write racing the
        // daily horizon roll-forward job, both of which funnel through this method) serialize
        // instead of interleaving their wholesale delete+insert and tripping the no-overlap GIST
        // exclusion constraint. The lock is transaction-scoped, so it auto-releases on commit or
        // rollback with no explicit unlock.
        lockGuide(guideId);

        LocalDate today = LocalDate.now(clock);
        AvailabilityHorizon horizon = new AvailabilityHorizon(today, today.plusDays(HORIZON_DAYS));

        List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
        List<AvailabilityExceptionEntity> guideExceptions = exceptions.findByGuideId(guideId);

        // Always resolve a CONCRETE exception zone and always call the 4-arg overload, so
        // rematerialize never propagates the pure engine's "exceptions-but-no-rules" throw for a
        // plausible guide state (that throw stays in the pure 3-arg project for Task-2).
        // Precedence:
        //   1. settings row -> its timezone;
        //   2. else rules present -> the pure engine's MODE-of-rules heuristic (reused, not
        // copied);
        //   3. else -> DEFAULT_TIMEZONE (the guide_booking_settings.timezone DB default).
        String guideTimezone = resolveGuideTimezone(guideId, guideRules);
        ProjectionResult result =
                AvailabilityProjection.project(guideRules, guideExceptions, horizon, guideTimezone);

        Instant generatedAt = clock.instant();

        // Wholesale replace: drop the guide's current cache, then insert the freshly projected set.
        // The flush AFTER the deletes is REQUIRED: without it Hibernate's action queue orders all
        // INSERTs before all DELETEs within one flush, so re-inserting an interval identical to an
        // as-yet-undeleted old row would spuriously trip excl_guide_occurrence_no_overlap (breaking
        // idempotency). Flushing forces the DELETEs to hit the DB before any INSERT.
        occurrences.deleteByGuideId(guideId);
        dstNotices.deleteByGuideId(guideId);
        occurrences.flush();

        List<GuideAvailabilityOccurrenceEntity> newOccurrences =
                result.intervals().stream()
                        .map(interval -> toOccurrence(guideId, interval, generatedAt))
                        .toList();
        List<GuideAvailabilityDstNoticeEntity> newNotices =
                result.dstAdjustedDays().stream()
                        .map(date -> toDstNotice(guideId, date, generatedAt))
                        .toList();

        try {
            occurrences.saveAll(newOccurrences);
            dstNotices.saveAll(newNotices);
            // One flush inside the try so the GIST constraint fires here (mapped below), not on a
            // later auto-flush outside this handler. occurrences.flush() flushes the whole shared
            // persistence context (both repos use the same EntityManager in this tx), so the notice
            // inserts are covered too — no separate dstNotices.flush() needed.
            occurrences.flush();
        } catch (DataIntegrityViolationException e) {
            // The projection coalesces the net-available set into a DISJOINT union before insert,
            // so
            // excl_guide_occurrence_no_overlap must never fire post-coalesce. If it does, a
            // projection/coalesce invariant has broken (NOT a guide-facing conflict) — surface it
            // as
            // an INTERNAL error with diagnostics, never a guide-visible message.
            throw new IllegalStateException(
                    "Materialized occurrences for guide "
                            + guideId
                            + " violated the no-overlap GIST invariant after coalesce (this"
                            + " indicates a projection/coalesce bug, not a user conflict). Projected"
                            + " intervals: "
                            + result.intervals(),
                    e);
        }
    }

    /**
     * Acquires the per-guide, TRANSACTION-scoped PostgreSQL advisory lock that serializes
     * concurrent {@link #rematerialize(UUID)} calls for one guide. The key is {@code
     * hashtextextended(guideId::text, 0)} — a stable 64-bit hash of the guide's canonical UUID
     * string. EVERY path that re-derives a guide's occurrences funnels through {@code
     * rematerialize} (guide-facing rule/exception/settings writes AND the daily {@link
     * OccurrenceHorizonJob} via {@link GuideHorizonClaimService#claimAndRematerialize(UUID)}), so
     * they all acquire this same lock with an identical key and contend correctly.
     *
     * <p><b>Why {@code pg_advisory_xact_lock} (not the session-scoped variant):</b> it
     * auto-releases when this transaction commits or rolls back — no explicit unlock, and no leaked
     * lock if the work throws. Because {@code rematerialize} runs at Spring {@code REQUIRED}
     * propagation (never {@code REQUIRES_NEW}), when a replace endpoint calls it inside the
     * caller's transaction the lock is held across that whole transaction — the delete/insert and
     * the rematerialize sit under one lock. Re-acquiring the same key twice in one transaction is a
     * harmless no-op (PostgreSQL advisory locks are re-entrant within a session/transaction).
     */
    private void lockGuide(UUID guideId) {
        GuideAdvisoryLock.acquire(entityManager, guideId);
    }

    /**
     * Resolves a concrete exception zone for the guide, never throwing: settings row -> its zone;
     * else rules present -> the pure engine's MODE-of-rules heuristic (reused directly); else ->
     * {@link #DEFAULT_TIMEZONE}. This is what lets a guide with exceptions but NO rules and NO
     * settings row still materialize (in the default zone) instead of the pure engine throwing.
     */
    private String resolveGuideTimezone(
            UUID guideId, List<GuideAvailabilityRuleEntity> guideRules) {
        Optional<GuideBookingSettingsEntity> guideSettings = settings.findByGuideId(guideId);
        if (guideSettings.isPresent()) {
            return guideSettings.get().getTimezone();
        }
        if (!guideRules.isEmpty()) {
            return AvailabilityProjection.resolveExceptionTimezone(guideRules);
        }
        return DEFAULT_TIMEZONE;
    }

    private static GuideAvailabilityOccurrenceEntity toOccurrence(
            UUID guideId, AvailabilityInterval interval, Instant generatedAt) {
        GuideAvailabilityOccurrenceEntity occurrence = new GuideAvailabilityOccurrenceEntity();
        occurrence.setId(UUID.randomUUID());
        occurrence.setGuideId(guideId);
        occurrence.setDuringStartAt(interval.startAt());
        occurrence.setDuringEndAt(interval.endAt());
        // source_* are informational only and lossy after coalesce (a net interval doesn't map 1:1
        // to a single input row) — left null, per the schema/entity contract.
        occurrence.setGeneratedAt(generatedAt);
        return occurrence;
    }

    private static GuideAvailabilityDstNoticeEntity toDstNotice(
            UUID guideId, LocalDate adjustedDate, Instant generatedAt) {
        GuideAvailabilityDstNoticeEntity notice = new GuideAvailabilityDstNoticeEntity();
        notice.setId(UUID.randomUUID());
        notice.setGuideId(guideId);
        notice.setAdjustedDate(adjustedDate);
        notice.setGeneratedAt(generatedAt);
        return notice;
    }
}
