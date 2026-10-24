package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReviewController — thin adapter: enforces the PARTICIPANT role, delegates to ReviewService, wraps
 * the result in the {@code {data, meta}} envelope. Business logic lives in ReviewService.
 */
@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock CurrentUser currentUser;
    @Mock ReviewService reviewService;

    private ReviewController controller() {
        return new ReviewController(currentUser, reviewService);
    }

    private static UserEntity participant() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static ReviewResponse mockReview() {
        return new ReviewResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                5,
                null,
                null,
                null,
                null,
                "Great",
                null,
                "PUBLISHED",
                null,
                null,
                null);
    }

    @Test
    void create_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participant();
        UUID bookingId = UUID.randomUUID();
        CreateReviewRequest req = new CreateReviewRequest(5, null, null, null, null, "Great", null);
        ReviewResponse review = mockReview();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(reviewService.createReview(u, bookingId, req)).thenReturn(review);

        assertSame(review, controller().create(bookingId, req).data());
        verify(reviewService).createReview(u, bookingId, req);
    }

    @Test
    void get_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participant();
        UUID bookingId = UUID.randomUUID();
        ReviewResponse review = mockReview();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(reviewService.getReviewForBooking(u, bookingId)).thenReturn(review);

        assertSame(review, controller().get(bookingId).data());
        verify(reviewService).getReviewForBooking(u, bookingId);
    }
}
