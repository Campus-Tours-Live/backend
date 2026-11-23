package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.web.dto.GuideReviewResponseRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The CTL-119 write path against a concurrent admin removal, end to end on real Postgres. {@code
 * respondToReview} runs in an outer transaction (it loads the PUBLISHED review and sets the reply),
 * and an admin removal commits in a nested {@code REQUIRES_NEW} transaction before the reply's
 * transaction commits. The review must stay REMOVED and keep the reply. It relies on {@link
 * ReviewEntity}'s {@code @DynamicUpdate}, and fails without it (the reply's commit restores
 * PUBLISHED).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReviewService.class, ReviewWriteFixtures.class})
@Timeout(30)
class GuideReplyConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewWriteFixtures fixtures;

    @Test
    void respondToReview_thenConcurrentRemovalCommits_staysRemovedWithTheReply() {
        UUID reviewId = fixtures.seedPublishedReview();
        UUID guideId = reviews.findById(reviewId).orElseThrow().getGuideId();

        newTx().executeWithoutResult(
                        outer -> {
                            service.respondToReview(
                                    guideId,
                                    reviewId,
                                    new GuideReviewResponseRequest("Thanks for coming!"));
                            newTx().executeWithoutResult(
                                            inner -> {
                                                ReviewEntity fresh =
                                                        reviews.findById(reviewId).orElseThrow();
                                                fresh.setStatus(ReviewStatus.REMOVED);
                                                reviews.save(fresh);
                                            });
                        });

        ReviewEntity after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(after.getGuideResponse()).isEqualTo("Thanks for coming!");
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
}
