package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.web.dto.ModerateReviewRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The CTL-123 write path against a concurrent guide reply, end to end on real Postgres.
 *
 * <p>{@code moderateReview} loads the review and sets REMOVED, but its UPDATE only reaches the
 * database when the aggregate recompute (a native query) flushes. The spied {@code
 * recomputeRatingAggregate} commits a guide reply in a separate {@code REQUIRES_NEW} transaction at
 * exactly that point (after the load, before the flush), then runs the real recompute. The review
 * must end REMOVED and keep the reply, and the aggregates must drop it. It relies on {@link
 * ReviewEntity}'s {@code @DynamicUpdate}, and fails without it (moderation's flush erases the
 * reply).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReviewService.class, ReviewWriteFixtures.class})
@Timeout(30)
class ReviewModerationConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired TourOfferingRepository offerings;
    @Autowired ReviewWriteFixtures fixtures;
    @MockitoSpyBean GuideProfileRepository guides;

    @Test
    void moderateReviewRemoval_withAReplyCommittedBeforeItsFlush_keepsTheReply() {
        UUID reviewId = fixtures.seedPublishedReview();
        // A spy of a Spring Data repository (an interface proxy) can't callRealMethod; its default
        // answer is what forwards calls to the real repository, so hand the call to that.
        Answer<?> forwardToReal =
                mockingDetails(guides).getMockCreationSettings().getDefaultAnswer();
        doAnswer(
                        invocation -> {
                            commitReplyInNewTx(reviewId, "Thanks for coming!");
                            return forwardToReal.answer(invocation);
                        })
                .when(guides)
                .recomputeRatingAggregate(any());

        service.moderateReview(reviewId, new ModerateReviewRequest("REMOVED"));

        ReviewEntity after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(after.getGuideResponse()).isEqualTo("Thanks for coming!");
        assertThat(offerings.findById(after.getTourOfferingId()).orElseThrow().getReviewCount())
                .isZero();
    }

    private void commitReplyInNewTx(UUID reviewId, String reply) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(
                status -> {
                    ReviewEntity fresh = reviews.findById(reviewId).orElseThrow();
                    fresh.setGuideResponse(reply);
                    reviews.save(fresh);
                });
    }
}
