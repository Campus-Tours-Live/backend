package com.CampusToursLive.domain.availability;

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
 * Availability persistence layer (CTL-54 Task 3): re-derives a guide's materialized availability
 * occurrences (and DST notices) from their rules + exceptions over the rolling horizon and
 * WHOLESALE-REPLACES them in one transaction. The pure math lives in {@link AvailabilityProjection}
 * (Task 2); this class is only the "fetch inputs, project, replace outputs" wiring.
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

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideAvailabilityOccurrenceRepository occurrences;
    private final GuideAvailabilityDstNoticeRepository dstNotices;
    private final GuideBookingSettingsRepository settings;
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
            GuideBookingSettingsRepository settings) {
        this(rules, exceptions, occurrences, dstNotices, settings, Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to pin "today" (and thus the horizon). */
    AvailabilityService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideAvailabilityOccurrenceRepository occurrences,
            GuideAvailabilityDstNoticeRepository dstNotices,
            GuideBookingSettingsRepository settings,
            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.occurrences = occurrences;
        this.dstNotices = dstNotices;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Re-derives and wholesale-replaces the guide's materialized occurrences and DST notices over
     * the rolling {@link #HORIZON_DAYS} horizon, in one transaction.
     */
    @Transactional
    public void rematerialize(UUID guideId) {
        LocalDate today = LocalDate.now(clock);
        AvailabilityHorizon horizon = new AvailabilityHorizon(today, today.plusDays(HORIZON_DAYS));

        List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
        List<AvailabilityExceptionEntity> guideExceptions = exceptions.findByGuideId(guideId);

        // Resolve the exception timezone from the guide's settings row when present (Task 3's fix:
        // a guide with exceptions but no active rules still projects correctly). With no settings
        // row, fall back to the pure engine's mode heuristic (resolve the zone from the rules).
        Optional<GuideBookingSettingsEntity> guideSettings = settings.findByGuideId(guideId);
        ProjectionResult result =
                guideSettings
                        .map(
                                s ->
                                        AvailabilityProjection.project(
                                                guideRules,
                                                guideExceptions,
                                                horizon,
                                                s.getTimezone()))
                        .orElseGet(
                                () ->
                                        AvailabilityProjection.project(
                                                guideRules, guideExceptions, horizon));

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
            // Flush inside the try so the GIST constraint fires here (mapped below), not on a later
            // auto-flush outside this handler.
            occurrences.flush();
            dstNotices.flush();
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
