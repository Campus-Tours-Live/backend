package com.CampusToursLive.domain.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sweep that retires stale DRAFT cart items (CTL-91) — those whose scheduled start has
 * passed or that have out-lived the cart-retention window. The work (and its transaction) lives in
 * {@link BookingService#expireStaleCartItems()}; this component only drives it on a cron, mirroring
 * {@code OccurrenceHorizonJob}.
 */
@Component
public class CartExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CartExpiryJob.class);

    private final BookingService bookingService;

    public CartExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Cron default: {@code 0 30 3 * * *} — 03:30 UTC daily (a low-traffic time, offset from the
     * availability horizon job at 03:00). Overridable via {@code cart.expiry-job.cron}.
     */
    @Scheduled(cron = "${cart.expiry-job.cron:0 30 3 * * *}", zone = "UTC")
    public void sweep() {
        try {
            int expired = bookingService.expireStaleCartItems();
            if (expired > 0) {
                log.info("Cart expiry sweep retired {} stale DRAFT item(s)", expired);
            }
        } catch (RuntimeException e) {
            // A failed sweep must never crash the scheduler thread — log and let the next tick
            // retry.
            log.warn("Cart expiry sweep failed", e);
        }
    }
}
