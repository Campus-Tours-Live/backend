package com.CampusToursLive.domain.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link ReviewEntity}. */
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    /**
     * The review for a booking, if one exists (mirrors the {@code UNIQUE(booking_id)} constraint).
     */
    Optional<ReviewEntity> findByBookingId(UUID bookingId);

    /** Cheap pre-check for the one-review-per-booking rule before attempting an insert. */
    boolean existsByBookingId(UUID bookingId);
}
