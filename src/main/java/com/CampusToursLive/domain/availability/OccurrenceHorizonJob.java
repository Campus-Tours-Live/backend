package com.CampusToursLive.domain.availability;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily scheduled roll-forward of the materialized availability horizon.
 *
 * <p>{@link AvailabilityService#rematerialize(UUID)} (Task 3) always materializes {@code [now,
 * now+HORIZON_DAYS)} relative to whenever it's called — a guide who never edits a rule or exception
 * would otherwise see their materialized coverage shrink toward "today" as real time passes (their
 * last write is further and further in the past). This job re-invokes {@code rematerialize} for
 * every guide, daily, so the far edge of their coverage keeps rolling forward with the calendar and
 * Task 6's booking-containment check never false-rejects a far-future booking that is still within
 * {@code HORIZON_DAYS} of "now".
 *
 * <p><b>Concurrency + failure isolation are owned HERE</b>, not by {@code rematerialize} itself
 * (which is a plain, unlocked, idempotent per-guide operation): each guide is claimed via {@link
 * GuideHorizonClaimService#claimAndRematerialize(UUID)} (SKIP LOCKED + its own REQUIRES_NEW
 * transaction) inside a try/catch, so (a) two scheduler instances racing the same guide never both
 * materialize it at once, and (b) one guide throwing (bad data, a transient error, ...) is logged
 * and skipped WITHOUT aborting the rest of the batch. Re-running the whole job is always safe:
 * {@code rematerialize} is idempotent, and a guide skipped this run is simply picked up on the next
 * tick.
 */
@Component
public class OccurrenceHorizonJob {

    private static final Logger log = LoggerFactory.getLogger(OccurrenceHorizonJob.class);

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideHorizonClaimService claimService;

    @Autowired
    public OccurrenceHorizonJob(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideHorizonClaimService claimService) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.claimService = claimService;
    }

    /**
     * Cron default: {@code 0 0 3 * * *} — 03:00 UTC daily, a fixed, low-traffic time. Overridable
     * via the {@code availability.horizon-job.cron} property (ops/tests can retune the schedule
     * without a code change).
     */
    @Scheduled(cron = "${availability.horizon-job.cron:0 0 3 * * *}", zone = "UTC")
    public void rollHorizonForward() {
        Set<UUID> guideIds = new LinkedHashSet<>();
        guideIds.addAll(rules.findDistinctGuideIds());
        guideIds.addAll(exceptions.findDistinctGuideIds());

        for (UUID guideId : guideIds) {
            try {
                claimService.claimAndRematerialize(guideId);
            } catch (RuntimeException e) {
                // One bad guide must never abort the batch — log and move on to the next guide.
                log.warn("Horizon roll-forward failed for guide {}", guideId, e);
            }
        }
    }
}
