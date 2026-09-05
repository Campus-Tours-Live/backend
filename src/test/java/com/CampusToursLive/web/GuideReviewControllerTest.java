package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.GuideProfileSnapshot;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.security.RoleAccountContext;
import com.CampusToursLive.web.dto.GuideReviewResponseRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideReviewController — thin adapter: resolves the caller's guide profile id via requireGuide,
 * delegates to ReviewService, wraps the result in the {@code {data, meta}} envelope.
 */
@ExtendWith(MockitoExtension.class)
class GuideReviewControllerTest {

    @Mock CurrentUser currentUser;
    @Mock ReviewService reviewService;

    private GuideReviewController controller() {
        return new GuideReviewController(currentUser, reviewService);
    }

    private static GuideProfileSnapshot snapshotWithId(UUID profileId) {
        return new GuideProfileSnapshot(
                profileId,
                UUID.randomUUID(),
                "bio",
                "[\"en-US\"]",
                "[\"GENERAL_CAMPUS\"]",
                GuideStatus.VERIFIED,
                Instant.now(),
                Instant.now());
    }

    private static ProvisionedAccount account() {
        return new ProvisionedAccount(
                UUID.randomUUID(),
                "sub-1",
                "guide@example.com",
                "Ada",
                "Lovelace",
                "Ada Lovelace",
                AccountStatus.ACTIVE,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Set.of(UserRole.GUIDE));
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
                "PUBLISHED",
                "thanks!",
                null,
                null);
    }

    @Test
    void respond_passesCallersGuideProfileId_andWrapsResultInEnvelope() {
        UUID profileId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        GuideReviewResponseRequest req = new GuideReviewResponseRequest("thanks!");
        ReviewResponse updated = review();
        when(currentUser.requireGuide())
                .thenReturn(new RoleAccountContext.Guide(account(), snapshotWithId(profileId)));
        when(reviewService.respondToReview(profileId, reviewId, req)).thenReturn(updated);

        assertSame(updated, controller().respond(reviewId, req).data());
        verify(reviewService).respondToReview(profileId, reviewId, req);
    }
}
