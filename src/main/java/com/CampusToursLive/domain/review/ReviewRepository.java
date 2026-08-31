package com.CampusToursLive.domain.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link ReviewEntity}. */
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    /**
     * The review for a booking, if one exists (mirrors the {@code UNIQUE(booking_id)} constraint).
     */
    Optional<ReviewEntity> findByBookingId(UUID bookingId);

    /** Cheap pre-check for the one-review-per-booking rule before attempting an insert. */
    boolean existsByBookingId(UUID bookingId);

    /**
     * A page of a guide's reviews in a given status, newest first. Backed by the {@code
     * ix_reviews_guide (guide_id, status)} index; used by the public guide-profile surface with
     * {@code status = PUBLISHED}.
     */
    Page<ReviewEntity> findByGuideIdAndStatusOrderByPublishedAtDesc(
            UUID guideId, ReviewStatus status, Pageable pageable);

    /**
     * A page of an offering's reviews in a given status, newest first; used by the public
     * tour-detail surface with {@code status = PUBLISHED}.
     */
    Page<ReviewEntity> findByTourOfferingIdAndStatusOrderByPublishedAtDesc(
            UUID tourOfferingId, ReviewStatus status, Pageable pageable);
}
