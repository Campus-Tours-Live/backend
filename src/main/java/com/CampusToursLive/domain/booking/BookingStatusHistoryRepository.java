package com.CampusToursLive.domain.booking;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository
        extends JpaRepository<BookingStatusHistoryEntity, Long> {

    /** Chronological transition trail for one booking (oldest first). */
    List<BookingStatusHistoryEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
