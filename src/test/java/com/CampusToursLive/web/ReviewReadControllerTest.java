package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.web.dto.PublicReviewResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * ReviewReadController — thin adapter: delegates to ReviewService and wraps the page in {@code
 * {data: {items,...}, meta}}. No auth (the routes are permitAll); business logic lives in the
 * service.
 */
@ExtendWith(MockitoExtension.class)
class ReviewReadControllerTest {

    @Mock ReviewService reviewService;

    private ReviewReadController controller() {
        return new ReviewReadController(reviewService);
    }

    private static PublicReviewResponse review() {
        return new PublicReviewResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Pat P.",
                5,
                null,
                null,
                null,
                null,
                "great",
                null,
                null,
                null);
    }

    @Test
    void guideReviews_delegates_andWrapsPageInEnvelope() {
        UUID guideId = UUID.randomUUID();
        Page<PublicReviewResponse> page = new PageImpl<>(List.of(review()));
        when(reviewService.getGuideReviews(eq(guideId), eq(0), eq(20))).thenReturn(page);

        var env = controller().guideReviews(guideId, 0, 20);
        assertEquals(page.getContent(), env.data().items());
        assertEquals(1, env.data().totalElements());
        verify(reviewService).getGuideReviews(guideId, 0, 20);
    }

    @Test
    void offeringReviews_delegates_andWrapsPageInEnvelope() {
        UUID offeringId = UUID.randomUUID();
        Page<PublicReviewResponse> page = new PageImpl<>(List.of(review()));
        when(reviewService.getOfferingReviews(eq(offeringId), eq(1), eq(5))).thenReturn(page);

        var env = controller().offeringReviews(offeringId, 1, 5);
        assertEquals(page.getContent(), env.data().items());
        verify(reviewService).getOfferingReviews(offeringId, 1, 5);
    }
}
