package com.CampusToursLive.domain.booking;

import java.time.Clock;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases expired guide-response holds using their existing deadline, without changing payments.
 */
@Service
public class BookingExpiryService {
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final Clock clock;

    public BookingExpiryService(
            BookingRepository bookings, BookingStatusHistoryRepository history, Clock clock) {
        this.bookings = bookings;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Locks at most 100 due rows until both state and audit commit. SKIP LOCKED coordinates
     * multiple instances; the entity version prevents a stale accept/decline from overwriting
     * expiry. Delaying the first tick avoids writes during application startup.
     */
    @Scheduled(
            fixedDelayString = "${booking.expiry.delay-ms:60000}",
            initialDelayString = "${booking.expiry.initial-delay-ms:60000}")
    @Transactional
    public int expireDueRequests() {
        List<BookingEntity> due = bookings.claimExpiredGuideRequests(clock.instant());
        for (BookingEntity booking : due) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setGuideResponseDeadlineAt(null);
            BookingStatusHistoryEntity event = new BookingStatusHistoryEntity();
            event.setBookingId(booking.getId());
            event.setPreviousStatus(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
            event.setNewStatus(BookingStatus.EXPIRED);
            event.setActorType(BookingActor.SYSTEM);
            event.setReasonCode("GUIDE_RESPONSE_EXPIRED");
            history.save(event);
        }
        bookings.flush();
        return due.size();
    }
}
