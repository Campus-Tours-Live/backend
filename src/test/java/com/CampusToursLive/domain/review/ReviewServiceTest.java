package com.CampusToursLive.domain.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviews;
    @Mock BookingRepository bookings;
    @Mock GuideProfileRepository guides;
    @Mock TourOfferingRepository offerings;

    private ReviewService service() {
        return new ReviewService(reviews, bookings, guides, offerings);
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
}
