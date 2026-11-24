package com.CampusToursLive.domain.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-completes bookings once the tour is over (CTL-141). Nothing else moves a booking from
 * CONFIRMED to COMPLETED, and reviews (CTL-107) can only be written for a COMPLETED booking, so
 * without this a guide who never marks the tour done blocks the participant's review.
 *
 * <p>A booking is due when its scheduled end is more than {@code booking.auto-complete.grace-min}
 * (default 24h) in the past; the grace window leaves the guide time to report a no-show, which
 * moves it out of CONFIRMED so this never overrides it. Rows are claimed in bounded batches with
 * {@code FOR UPDATE SKIP LOCKED}; the entity {@code @Version} stops a stale guide action from
 * overwriting a completion (or vice versa). Driven by {@link BookingCompletionJob}.
 */
@Service
public class BookingCompletionService {

    static final String REASON = "AUTO_COMPLETED";

    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final Clock clock;
    private final Duration grace;

    public BookingCompletionService(
            BookingRepository bookings,
            BookingStatusHistoryRepository history,
            Clock clock,
            @Value("${booking.auto-complete.grace-min:1440}") long graceMin) {
        if (graceMin < 0) {
            throw new IllegalArgumentException("booking.auto-complete.grace-min must be >= 0");
        }
        this.bookings = bookings;
        this.history = history;
        this.clock = clock;
        this.grace = Duration.ofMinutes(graceMin);
    }

    /**
     * Completes up to {@code limit} due bookings in one transaction and returns how many: each
     * becomes COMPLETED with {@code completedAt = now} and a SYSTEM {@code booking_status_history}
     * row ({@value #REASON}). Returns fewer than {@code limit} once nothing more is due.
     */
    @Transactional
    public int completeEndedBatch(int limit) {
        Instant now = clock.instant();
        List<BookingEntity> due = bookings.claimEndedBookings(now.minus(grace), limit);
        for (BookingEntity b : due) {
            BookingStatus previous = b.getStatus();
            b.setStatus(BookingStatus.COMPLETED);
            b.setCompletedAt(now);
            BookingStatusHistoryEntity h = new BookingStatusHistoryEntity();
            h.setBookingId(b.getId());
            h.setPreviousStatus(previous);
            h.setNewStatus(BookingStatus.COMPLETED);
            h.setActorType(BookingActor.SYSTEM);
            h.setReasonCode(REASON);
            history.save(h);
        }
        bookings.flush();
        return due.size();
    }
}
