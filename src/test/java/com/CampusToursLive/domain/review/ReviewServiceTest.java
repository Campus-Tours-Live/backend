package com.CampusToursLive.domain.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviews;
    @Mock BookingRepository bookings;
    @Mock GuideProfileRepository guides;
    @Mock TourOfferingRepository offerings;
    @Mock UserRepository users;

    private ReviewService service() {
        return new ReviewService(reviews, bookings, guides, offerings, users);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static BookingEntity completedBooking(UUID participantId) {
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setParticipantUserId(participantId);
        b.setGuideId(UUID.randomUUID());
        b.setTourOfferingId(UUID.randomUUID());
        b.setStatus(BookingStatus.COMPLETED);
        return b;
    }

    private static CreateReviewRequest req(Integer overall) {
        return new CreateReviewRequest(overall, null, null, null, null, null, null);
    }

    @Test
    void createReview_persistsPublished_recomputesBothAggregates_andReturnsResponse() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.existsByBookingId(booking.getId())).thenReturn(false);

        CreateReviewRequest request =
                new CreateReviewRequest(5, 5, 4, 5, 5, "  Great tour  ", "  quiet nit  ");
        ReviewResponse resp = service().createReview(participant, booking.getId(), request);

        // Persisted as PUBLISHED with a publish timestamp, denormalized from the booking.
        var saved = org.mockito.ArgumentCaptor.forClass(ReviewEntity.class);
        verify(reviews).saveAndFlush(saved.capture());
        ReviewEntity entity = saved.getValue();
        assertEquals(ReviewStatus.PUBLISHED, entity.getStatus());
        assertEquals(booking.getGuideId(), entity.getGuideId());
        assertEquals(booking.getTourOfferingId(), entity.getTourOfferingId());
        assertEquals(participant.getId(), entity.getParticipantUserId());
        assertEquals((short) 5, entity.getOverallRating());
        assertEquals((short) 4, entity.getCommunicationRating());
        // Free text is trimmed; blanks collapse to null (tested separately).
        assertEquals("Great tour", entity.getComment());
        assertEquals("quiet nit", entity.getPrivateFeedback());

        // Aggregates recomputed for BOTH the guide and the offering.
        verify(guides).recomputeRatingAggregate(booking.getGuideId());
        verify(offerings).recomputeRatingAggregate(booking.getTourOfferingId());

        assertEquals("PUBLISHED", resp.status());
        assertEquals(5, resp.overallRating());
        assertEquals(4, resp.communicationRating());
    }

    @Test
    void createReview_blankFreeText_storedAsNull() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.existsByBookingId(booking.getId())).thenReturn(false);

        service()
                .createReview(
                        participant,
                        booking.getId(),
                        new CreateReviewRequest(5, null, null, null, null, "   ", ""));

        var saved = org.mockito.ArgumentCaptor.forClass(ReviewEntity.class);
        verify(reviews).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getComment());
        assertNull(saved.getValue().getPrivateFeedback());
        assertNull(saved.getValue().getKnowledgeRating());
    }

    @Test
    void createReview_missingBooking_throwsNotFound() {
        UserEntity participant = user(UUID.randomUUID());
        UUID bookingId = UUID.randomUUID();
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service().createReview(participant, bookingId, req(5)));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void createReview_bookingOwnedByAnotherUser_throwsNotFound_noLeak() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity someoneElses = completedBooking(UUID.randomUUID());
        when(bookings.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

        assertThrows(
                NotFoundException.class,
                () -> service().createReview(participant, someoneElses.getId(), req(5)));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void createReview_bookingNotCompleted_throwsValidation() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(
                ValidationException.class,
                () -> service().createReview(participant, booking.getId(), req(5)));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void createReview_alreadyReviewed_throwsConflict() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.existsByBookingId(booking.getId())).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service().createReview(participant, booking.getId(), req(5)));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void createReview_missingOverallRating_throwsValidation() {
        UserEntity participant = user(UUID.randomUUID());
        assertThrows(
                ValidationException.class,
                () -> service().createReview(participant, UUID.randomUUID(), req(null)));
        verify(bookings, never()).findById(any());
    }

    @Test
    void createReview_overallRatingOutOfRange_throwsValidation() {
        UserEntity participant = user(UUID.randomUUID());
        assertThrows(
                ValidationException.class,
                () -> service().createReview(participant, UUID.randomUUID(), req(6)));
        assertThrows(
                ValidationException.class,
                () -> service().createReview(participant, UUID.randomUUID(), req(0)));
    }

    @Test
    void createReview_subRatingOutOfRange_throwsValidation() {
        UserEntity participant = user(UUID.randomUUID());
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createReview(
                                        participant,
                                        UUID.randomUUID(),
                                        new CreateReviewRequest(
                                                5, 9, null, null, null, null, null)));
    }

    @Test
    void createReview_commentTooLong_throwsValidation() {
        UserEntity participant = user(UUID.randomUUID());
        String tooLong = "x".repeat(1001);
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createReview(
                                        participant,
                                        UUID.randomUUID(),
                                        new CreateReviewRequest(
                                                5, null, null, null, null, tooLong, null)));
    }

    @Test
    void createReview_uniqueViolationRace_mappedToConflict() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.existsByBookingId(booking.getId())).thenReturn(false);
        when(reviews.saveAndFlush(any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate key value violates unique constraint"
                                        + " \"reviews_booking_id_key\""));

        assertThrows(
                ConflictException.class,
                () -> service().createReview(participant, booking.getId(), req(5)));
        verify(guides, never()).recomputeRatingAggregate(any());
    }

    @Test
    void createReview_unrelatedIntegrityViolation_isRethrown() {
        UserEntity participant = user(UUID.randomUUID());
        BookingEntity booking = completedBooking(participant.getId());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.existsByBookingId(booking.getId())).thenReturn(false);
        when(reviews.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service().createReview(participant, booking.getId(), req(5)));
    }

    @Test
    void getReviewForBooking_ownReview_returnsResponseIncludingPrivateFeedback() {
        UserEntity participant = user(UUID.randomUUID());
        ReviewEntity review = new ReviewEntity();
        review.setId(UUID.randomUUID());
        review.setBookingId(UUID.randomUUID());
        review.setParticipantUserId(participant.getId());
        review.setGuideId(UUID.randomUUID());
        review.setTourOfferingId(UUID.randomUUID());
        review.setOverallRating((short) 4);
        review.setPrivateFeedback("for the guide only");
        review.setStatus(ReviewStatus.PUBLISHED);
        when(reviews.findByBookingId(review.getBookingId())).thenReturn(Optional.of(review));

        ReviewResponse resp = service().getReviewForBooking(participant, review.getBookingId());
        assertEquals(4, resp.overallRating());
        assertEquals("for the guide only", resp.privateFeedback());
    }

    @Test
    void getReviewForBooking_reviewOwnedByAnother_throwsNotFound() {
        UserEntity participant = user(UUID.randomUUID());
        ReviewEntity review = new ReviewEntity();
        review.setParticipantUserId(UUID.randomUUID()); // someone else
        review.setBookingId(UUID.randomUUID());
        when(reviews.findByBookingId(review.getBookingId())).thenReturn(Optional.of(review));

        assertThrows(
                NotFoundException.class,
                () -> service().getReviewForBooking(participant, review.getBookingId()));
    }

    @Test
    void getReviewForBooking_noReview_throwsNotFound() {
        UserEntity participant = user(UUID.randomUUID());
        UUID bookingId = UUID.randomUUID();
        when(reviews.findByBookingId(bookingId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service().getReviewForBooking(participant, bookingId));
    }

    @Test
    void getReviewForBooking_sameResponseFieldsPreserved() {
        // Guards the null-safe timestamp mapping (createdAt/publishedAt null on an unsaved entity).
        UserEntity participant = user(UUID.randomUUID());
        ReviewEntity review = new ReviewEntity();
        review.setId(UUID.randomUUID());
        review.setBookingId(UUID.randomUUID());
        review.setParticipantUserId(participant.getId());
        review.setGuideId(UUID.randomUUID());
        review.setTourOfferingId(UUID.randomUUID());
        review.setOverallRating((short) 3);
        review.setStatus(ReviewStatus.PUBLISHED);
        when(reviews.findByBookingId(review.getBookingId())).thenReturn(Optional.of(review));

        ReviewResponse resp = service().getReviewForBooking(participant, review.getBookingId());
        assertNull(resp.createdAt());
        assertNull(resp.publishedAt());
        assertSame(null, resp.guideResponse());
    }

    // ── public read surfaces ────────────────────────────────────────────────

    private static ReviewEntity publishedReview(UUID guideId, UUID offeringId, UUID reviewerId) {
        ReviewEntity r = new ReviewEntity();
        r.setId(UUID.randomUUID());
        r.setBookingId(UUID.randomUUID());
        r.setParticipantUserId(reviewerId);
        r.setGuideId(guideId);
        r.setTourOfferingId(offeringId);
        r.setOverallRating((short) 5);
        r.setComment("great");
        r.setPrivateFeedback("secret — must never leak");
        r.setStatus(ReviewStatus.PUBLISHED);
        return r;
    }

    @Test
    void getGuideReviews_mapsPublicShape_resolvesNamesInOneBatch_andHidesPrivateFeedback() {
        UUID guideId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewEntity review = publishedReview(guideId, UUID.randomUUID(), reviewerId);
        Page<ReviewEntity> page = new PageImpl<>(List.of(review));
        when(reviews.findByGuideIdAndStatusOrderByPublishedAtDesc(
                        eq(guideId), eq(ReviewStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(page);
        UserEntity reviewer = user(reviewerId);
        reviewer.setDisplayName("Pat P.");
        when(users.findAllById(List.of(reviewerId))).thenReturn(List.of(reviewer));

        Page<PublicReviewResponse> result = service().getGuideReviews(guideId, 0, 20);

        assertEquals(1, result.getContent().size());
        PublicReviewResponse dto = result.getContent().get(0);
        assertEquals("Pat P.", dto.reviewerName());
        assertEquals(5, dto.overallRating());
        assertEquals("great", dto.comment());
        assertEquals(guideId.toString(), dto.guideId());
        // Batched exactly once — never per row (the N+1 the list must avoid).
        verify(users).findAllById(List.of(reviewerId));
    }

    @Test
    void getOfferingReviews_delegatesToOfferingQuery() {
        UUID offeringId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewEntity review = publishedReview(UUID.randomUUID(), offeringId, reviewerId);
        when(reviews.findByTourOfferingIdAndStatusOrderByPublishedAtDesc(
                        eq(offeringId), eq(ReviewStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(users.findAllById(List.of(reviewerId))).thenReturn(List.of(user(reviewerId)));

        Page<PublicReviewResponse> result = service().getOfferingReviews(offeringId, 0, 20);
        assertEquals(offeringId.toString(), result.getContent().get(0).offeringId());
    }

    @Test
    void getGuideReviews_missingReviewerRow_yieldsNullName() {
        UUID guideId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewEntity review = publishedReview(guideId, UUID.randomUUID(), reviewerId);
        when(reviews.findByGuideIdAndStatusOrderByPublishedAtDesc(
                        eq(guideId), eq(ReviewStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(users.findAllById(List.of(reviewerId))).thenReturn(List.of()); // reviewer not found

        Page<PublicReviewResponse> result = service().getGuideReviews(guideId, 0, 20);
        assertNull(result.getContent().get(0).reviewerName());
    }

    @Test
    void getGuideReviews_pageSizeClampedToMax_andNegativePageFloored() {
        UUID guideId = UUID.randomUUID();
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        when(reviews.findByGuideIdAndStatusOrderByPublishedAtDesc(
                        eq(guideId), eq(ReviewStatus.PUBLISHED), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service().getGuideReviews(guideId, -3, 9999);

        assertEquals(0, pageable.getValue().getPageNumber()); // negative floored to 0
        assertEquals(100, pageable.getValue().getPageSize()); // clamped to MAX_PAGE_SIZE
    }
}
