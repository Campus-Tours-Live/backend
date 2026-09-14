package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ModerateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReviewModerationController — thin adapter: enforces the ADMIN role, delegates to ReviewService,
 * wraps the result in the {@code {data, meta}} envelope.
 */
@ExtendWith(MockitoExtension.class)
class ReviewModerationControllerTest {

    @Mock CurrentUser currentUser;
    @Mock ReviewService reviewService;

    private ReviewModerationController controller() {
        return new ReviewModerationController(currentUser, reviewService);
    }

    private static ReviewResponse review() {
        return new ReviewResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                5,
                null,
                null,
                null,
                null,
                "great",
                null,
                "REMOVED",
                null,
                null,
                null);
    }

    @Test
    void moderate_requiresAdminRole_andWrapsResultInEnvelope() {
        UUID reviewId = UUID.randomUUID();
        ModerateReviewRequest req = new ModerateReviewRequest("REMOVED");
        ReviewResponse updated = review();
        when(reviewService.moderateReview(reviewId, req)).thenReturn(updated);

        assertSame(updated, controller().moderate(reviewId, req).data());
        verify(currentUser).requireNonProfileRole(UserRole.ADMIN);
        verify(reviewService).moderateReview(reviewId, req);
    }
}
