package com.CampusToursLive.domain.review;

import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.PublicReviewResponse;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tour reviews (CTL-107): a participant reviews their own COMPLETED booking, and the guide's /
 * offering's rating aggregates are recomputed.
 *
 * <p><b>MVP policy.</b> A review is created already PUBLISHED (there is no moderation surface yet),
 * so it counts toward {@code avg_rating} / {@code review_count} immediately; PENDING_MODERATION and
 * the moderation transitions are a deferred follow-up. Only PUBLISHED reviews feed the aggregates,
 * which are recomputed from the source of truth (never incremented in place), so the counts stay
 * correct even under concurrent publishes.
 *
 * <p><b>Dependency.</b> A review requires {@code booking.status == COMPLETED}. Nothing transitions
 * a booking to COMPLETED yet — that is the tour-completion / live-session phase — so in production
 * no booking is reviewable until that lands; the API and its tests are complete and ready for it.
 */
@Service
public class ReviewService {

    /** Rating bounds — mirrors the reviews_*_rating_check CHECK constraints (1-5). */
    private static final int MIN_RATING = 1;

    private static final int MAX_RATING = 5;

    /** Cap on the free-text columns (comment, private feedback) — both TEXT. */
    private static final int MAX_FREE_TEXT_LENGTH = 1000;

    /** Upper bound on the public read page size — keeps a single request's fan-out bounded. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final GuideProfileRepository guides;
    private final TourOfferingRepository offerings;
    private final UserRepository users;

    public ReviewService(
            ReviewRepository reviews,
            BookingRepository bookings,
            GuideProfileRepository guides,
            TourOfferingRepository offerings,
            UserRepository users) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.guides = guides;
        this.offerings = offerings;
        this.users = users;
    }

    /**
     * Create the participant's review of their own COMPLETED booking, then recompute the guide's
     * and offering's rating aggregates. Auto-publishes (MVP).
     *
     * @throws NotFoundException the booking does not exist or is not owned by the caller (a
     *     non-owner gets the same 404 as a missing booking — no existence leak).
     * @throws ValidationException a rating is out of range, {@code overallRating} is missing, a
     *     free-text field is too long, or the booking is not COMPLETED.
     * @throws ConflictException the booking already has a review ({@code REVIEW_ALREADY_EXISTS}).
     */
    @Transactional
    public ReviewResponse createReview(
            UserEntity participant, UUID bookingId, CreateReviewRequest req) {
        short overall = requireRating(req.overallRating(), "overallRating");
        Short knowledge = optionalRating(req.knowledgeRating(), "knowledgeRating");
        Short communication = optionalRating(req.communicationRating(), "communicationRating");
        Short friendliness = optionalRating(req.friendlinessRating(), "friendlinessRating");
        Short helpfulness = optionalRating(req.helpfulnessRating(), "helpfulnessRating");
        String comment = boundedText(req.comment(), "comment");
        String privateFeedback = boundedText(req.privateFeedback(), "privateFeedback");

        BookingEntity booking = requireOwnedBooking(bookingId, participant.getId());
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ValidationException("A booking can only be reviewed once it is completed");
        }
        if (reviews.existsByBookingId(bookingId)) {
            throw ConflictException.reviewAlreadyExists(bookingId.toString());
        }

        ReviewEntity review = new ReviewEntity();
        review.setId(UUID.randomUUID());
        review.setBookingId(bookingId);
        review.setParticipantUserId(participant.getId());
        review.setGuideId(booking.getGuideId());
        review.setTourOfferingId(booking.getTourOfferingId());
        review.setOverallRating(overall);
        review.setKnowledgeRating(knowledge);
        review.setCommunicationRating(communication);
        review.setFriendlinessRating(friendliness);
        review.setHelpfulnessRating(helpfulness);
        review.setComment(comment);
        review.setPrivateFeedback(privateFeedback);
        // MVP: auto-publish (no moderation surface yet), so it counts toward the aggregates now.
        review.setStatus(ReviewStatus.PUBLISHED);
        review.setPublishedAt(Instant.now());

        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race on UNIQUE(booking_id): another concurrent request reviewed this booking
            // between our existsByBookingId check and the insert. Map to the same 409 as the
            // pre-check; anything else is a real integrity failure and must propagate.
            if (isDuplicateBookingReview(ex)) {
                throw ConflictException.reviewAlreadyExists(bookingId.toString());
            }
            throw ex;
        }

        // Recompute from source of truth (PUBLISHED reviews) — keeps guide + offering aggregates,
        // which discovery ranking reads, correct without a read-modify-write race.
        guides.recomputeRatingAggregate(booking.getGuideId());
        offerings.recomputeRatingAggregate(booking.getTourOfferingId());

        return toResponse(review);
    }

    /**
     * The caller's own review for a booking.
     *
     * @throws NotFoundException no review exists for the booking, or it is not the caller's (same
     *     404 either way — no leak).
     */
    @Transactional(readOnly = true)
    public ReviewResponse getReviewForBooking(UserEntity participant, UUID bookingId) {
        ReviewEntity review =
                reviews.findByBookingId(bookingId)
                        .filter(r -> r.getParticipantUserId().equals(participant.getId()))
                        .orElseThrow(() -> new NotFoundException("Review not found"));
        return toResponse(review);
    }

    /**
     * A page of a guide's PUBLISHED reviews, newest first — the public guide-profile surface.
     * Non-published reviews and {@code privateFeedback} are never exposed here.
     */
    @Transactional(readOnly = true)
    public Page<PublicReviewResponse> getGuideReviews(UUID guideId, int page, int size) {
        return toPublicPage(
                reviews.findByGuideIdAndStatusOrderByPublishedAtDesc(
                        guideId, ReviewStatus.PUBLISHED, pageRequest(page, size)));
    }

    /**
     * A page of an offering's PUBLISHED reviews, newest first — the public tour-detail surface.
     * Non-published reviews and {@code privateFeedback} are never exposed here.
     */
    @Transactional(readOnly = true)
    public Page<PublicReviewResponse> getOfferingReviews(UUID offeringId, int page, int size) {
        return toPublicPage(
                reviews.findByTourOfferingIdAndStatusOrderByPublishedAtDesc(
                        offeringId, ReviewStatus.PUBLISHED, pageRequest(page, size)));
    }

    /**
     * Map a page of reviews to the public shape, resolving reviewer display names in ONE batch
     * ({@code findAllById}) rather than per row — the N+1 the list surfaces must avoid.
     */
    private Page<PublicReviewResponse> toPublicPage(Page<ReviewEntity> page) {
        List<UUID> reviewerIds =
                page.getContent().stream()
                        .map(ReviewEntity::getParticipantUserId)
                        .distinct()
                        .toList();
        // HashMap#put (not Collectors.toMap, which NPEs on a null value) so a reviewer with a null
        // display name resolves to a null name rather than blowing up the whole page.
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(reviewerIds).forEach(u -> names.put(u.getId(), u.getDisplayName()));
        return page.map(r -> toPublicResponse(r, names.get(r.getParticipantUserId())));
    }

    private static PageRequest pageRequest(int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(boundedPage, boundedSize);
    }

    private static PublicReviewResponse toPublicResponse(ReviewEntity r, String reviewerName) {
        return new PublicReviewResponse(
                r.getId().toString(),
                r.getGuideId().toString(),
                r.getTourOfferingId().toString(),
                reviewerName,
                r.getOverallRating(),
                toInteger(r.getKnowledgeRating()),
                toInteger(r.getCommunicationRating()),
                toInteger(r.getFriendlinessRating()),
                toInteger(r.getHelpfulnessRating()),
                r.getComment(),
                r.getGuideResponse(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
                r.getPublishedAt() == null ? null : r.getPublishedAt().toString());
    }

    private BookingEntity requireOwnedBooking(UUID bookingId, UUID participantUserId) {
        return bookings.findById(bookingId)
                .filter(b -> b.getParticipantUserId().equals(participantUserId))
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private static short requireRating(Integer value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        return checkRange(value, field);
    }

    private static Short optionalRating(Integer value, String field) {
        return value == null ? null : checkRange(value, field);
    }

    private static short checkRange(int value, String field) {
        if (value < MIN_RATING || value > MAX_RATING) {
            throw new ValidationException(
                    field + " must be between " + MIN_RATING + " and " + MAX_RATING);
        }
        return (short) value;
    }

    private static String boundedText(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_FREE_TEXT_LENGTH) {
            throw new ValidationException(
                    field + " must be at most " + MAX_FREE_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }

    private static boolean isDuplicateBookingReview(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains("reviews_booking_id_key");
    }

    private static ReviewResponse toResponse(ReviewEntity r) {
        return new ReviewResponse(
                r.getId().toString(),
                r.getBookingId().toString(),
                r.getTourOfferingId().toString(),
                r.getOverallRating(),
                toInteger(r.getKnowledgeRating()),
                toInteger(r.getCommunicationRating()),
                toInteger(r.getFriendlinessRating()),
                toInteger(r.getHelpfulnessRating()),
                r.getComment(),
                r.getPrivateFeedback(),
                r.getStatus().name(),
                r.getGuideResponse(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
                r.getPublishedAt() == null ? null : r.getPublishedAt().toString());
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
