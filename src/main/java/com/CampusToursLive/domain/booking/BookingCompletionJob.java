package com.CampusToursLive.domain.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver for {@link BookingCompletionService} (CTL-141), mirroring {@code
 * OccurrenceHorizonJob}. Each tick completes due bookings in batches of {@link #BATCH_SIZE}, each
 * batch in its own transaction, until a short batch shows nothing more is due (capped at {@link
 * #MAX_BATCHES_PER_TICK} so a large backlog can't monopolize the scheduler thread; the rest is
 * picked up next tick).
 */
@Component
public class BookingCompletionJob {

    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES_PER_TICK = 50;

    private static final Logger log = LoggerFactory.getLogger(BookingCompletionJob.class);

    private final BookingCompletionService completion;

    public BookingCompletionJob(BookingCompletionService completion) {
        this.completion = completion;
    }

    /**
     * Cron default: {@code 0 15 * * * *}, hourly at :15 UTC. Completion is not time-critical (the
     * 24h grace already delays it), and an hourly cron keeps clear of the 03:00/03:30 daily jobs.
     * Overridable via {@code booking.auto-complete.cron}.
     */
    @Scheduled(cron = "${booking.auto-complete.cron:0 15 * * * *}", zone = "UTC")
    public void sweep() {
        int total = 0;
        try {
            for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
                int done = completion.completeEndedBatch(BATCH_SIZE);
                total += done;
                if (done < BATCH_SIZE) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            // Never let a failed sweep kill the scheduler thread: log and retry next tick.
            log.warn("Booking auto-complete sweep failed after completing {} booking(s)", total, e);
            return;
        }
        if (total > 0) {
            log.info("Booking auto-complete sweep completed {} booking(s)", total);
        }
    }
}
