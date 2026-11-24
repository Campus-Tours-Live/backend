package com.CampusToursLive.domain.reschedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sweep that expires reschedule proposals left pending past their {@code expiresAt}
 * (CTL-52, design §16.3: a pending proposal auto-expires and the booking is untouched). The work
 * and its transaction live in {@link RescheduleService#expirePastDueProposals()}; this component
 * only drives it on a cron, mirroring {@code OccurrenceHorizonJob}. Proposals are also expired
 * lazily when a new one is proposed for the same booking, so the sweep is for an accurate board,
 * not for correctness.
 */
@Component
public class RescheduleExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RescheduleExpiryJob.class);

    private final RescheduleService rescheduleService;

    public RescheduleExpiryJob(RescheduleService rescheduleService) {
        this.rescheduleService = rescheduleService;
    }

    /**
     * Cron default: {@code 0 *&#47;15 * * * *} (every 15 minutes, UTC). Proposals live at most 48h,
     * so a 15-minute lag keeps the board close to real time at negligible cost. Overridable via
     * {@code reschedule.expiry-job.cron}.
     */
    @Scheduled(cron = "${reschedule.expiry-job.cron:0 */15 * * * *}", zone = "UTC")
    public void sweep() {
        try {
            int expired = rescheduleService.expirePastDueProposals();
            if (expired > 0) {
                log.info("Reschedule expiry sweep expired {} proposal(s)", expired);
            }
        } catch (RuntimeException e) {
            // A failed sweep must never crash the scheduler thread: log and let the next tick
            // retry.
            log.warn("Reschedule expiry sweep failed", e);
        }
    }
}
