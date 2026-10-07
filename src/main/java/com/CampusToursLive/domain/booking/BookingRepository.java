package com.CampusToursLive.domain.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    /**
     * Next confirmed tour: the single soonest CONFIRMED booking for a participant that has not yet
     * started. Used for the "Next Tour" card on the participant dashboard.
     */
    Optional<BookingEntity>
            findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                    UUID participantUserId, BookingStatus status, Instant after);

    /**
     * Upcoming bookings: all active-lifecycle bookings for a participant that have not yet started,
     * ordered chronologically. Callers pass the statuses they consider "upcoming" (e.g. CONFIRMED +
     * PENDING_GUIDE_ACCEPTANCE + PENDING_PAYMENT_AUTH).
     */
    List<BookingEntity>
            findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                    UUID participantUserId, List<BookingStatus> statuses, Instant after);

    /**
     * Count bookings in any of the given statuses for a participant. Used to compute the
     * pending-actions counts (payments to finish, waiting for guide).
     */
    long countByParticipantUserIdAndStatusIn(UUID participantUserId, List<BookingStatus> statuses);

    /** A participant's own booking — scopes every write so users can only touch their own rows. */
    Optional<BookingEntity> findByIdAndParticipantUserId(UUID id, UUID participantUserId);

    /** One cart item: the participant's own booking in a given status (cart = status DRAFT). */
    Optional<BookingEntity> findByIdAndParticipantUserIdAndStatus(
            UUID id, UUID participantUserId, BookingStatus status);

    /**
     * An existing cart item for the same tour and start time — the idempotency key for add-to-cart
     * (participant + offering + scheduledStartAt + status DRAFT). Re-adding returns this row rather
     * than inserting a duplicate (CTL-83).
     */
    Optional<BookingEntity> findByParticipantUserIdAndTourOfferingIdAndScheduledStartAtAndStatus(
            UUID participantUserId,
            UUID tourOfferingId,
            Instant scheduledStartAt,
            BookingStatus status);

    /** All of a participant's bookings in one status, oldest first (cart listing / checkout). */
    List<BookingEntity> findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
            UUID participantUserId, BookingStatus status);

    /** Cart size, for the max-items cap. */
    long countByParticipantUserIdAndStatus(UUID participantUserId, BookingStatus status);

    /**
     * A guide's bookings in one status scheduled to start at or after {@code from}, chronological.
     * CTL-54 Task 7 ("(A) allow + notify") uses this — scoped to {@code CONFIRMED} — to find future
     * bookings an availability edit may have left uncovered, so the write's response can warn the
     * guide without ever mutating the booking.
     */
    List<BookingEntity>
            findByGuideIdAndStatusAndScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(
                    UUID guideId, BookingStatus status, Instant from);

    /**
     * Does any slot-holding booking already reserve part of [{@code newStart}, {@code newEnd}) for
     * this guide? Two intervals overlap iff existing.start &lt; new.end AND existing.end &gt;
     * new.start — hence the (end, start) argument order. Pre-checks the DB exclusion constraint
     * {@code excl_guide_no_overlap} so the common case fails with a friendly 422 instead of a
     * constraint violation.
     */
    boolean existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
            UUID guideId, List<BookingStatus> statuses, Instant newEnd, Instant newStart);

    /**
     * The guide's slot-holding bookings whose RESERVED interval overlaps {@code [windowStart,
     * windowEnd)} — same overlap direction/argument order as {@link
     * #existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan} but returns
     * the rows (not just existence): CTL-54 Task 8 slot generation needs each held booking's own
     * reserved interval to test against every candidate slot individually.
     */
    List<BookingEntity>
            findByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                    UUID guideId,
                    List<BookingStatus> statuses,
                    Instant windowEnd,
                    Instant windowStart);

    /**
     * Participant-side twin of the guide overlap check (constraint: excl_participant_no_overlap).
     */
    boolean
            existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                    UUID participantUserId,
                    List<BookingStatus> statuses,
                    Instant newEnd,
                    Instant newStart);

    /**
     * Count COMPLETED bookings for a participant that have no review yet. Uses a native SQL NOT
     * EXISTS subquery because ReviewEntity does not exist in the JPA model yet.
     */
    @Query(
            value =
                    "SELECT COUNT(*) FROM bookings b "
                            + "WHERE b.participant_user_id = :userId "
                            + "AND b.status = 'COMPLETED' "
                            + "AND NOT EXISTS ("
                            + "  SELECT 1 FROM reviews r WHERE r.booking_id = b.id"
                            + ")",
            nativeQuery = true)
    long countCompletedWithoutReview(@Param("userId") UUID userId);
}
