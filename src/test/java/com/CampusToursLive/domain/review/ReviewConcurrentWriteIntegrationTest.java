package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
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
 * Two-transaction regression for writers that own different columns of one {@code reviews} row
 * (guide reply → {@code guide_response}; admin moderation → {@code status}/{@code published_at}).
 *
 * <p>Each scenario loads the review in an outer transaction, lets a nested {@code REQUIRES_NEW}
 * transaction load, change and COMMIT the other column, and only then commits the outer one with
 * its stale copy, which is exactly the interleaving from the #72/#75 review. With an all-columns
 * UPDATE the outer commit writes the stale value back; {@link ReviewEntity}'s
 * {@code @DynamicUpdate} keeps both changes. Single-threaded and deterministic: the outer
 * transaction has not flushed when the nested one runs, so it holds no row lock (the timeout turns
 * any regression there into a failure, not a hang).
 *
 * <p>Class-level {@code NOT_SUPPORTED} turns off {@code @DataJpaTest}'s rollback-only wrapper,
 * since the scenarios need really committed transactions.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ReviewWriteFixtures.class)
@Timeout(30)
class ReviewConcurrentWriteIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewWriteFixtures fixtures;

    private UUID reviewId;

    @BeforeEach
    void seedPublishedReview() {
        reviewId = fixtures.seedPublishedReview();
    }

    @Test
    void staleReplyCommit_doesNotRestorePublished_overAConcurrentRemoval() {
        interleave(
                stale -> stale.setGuideResponse("Thanks for coming!"),
                concurrent -> concurrent.setStatus(ReviewStatus.REMOVED));

        ReviewEntity after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(after.getGuideResponse()).isEqualTo("Thanks for coming!");
    }

    @Test
    void staleModerationCommit_doesNotEraseAConcurrentReply() {
        interleave(
                stale -> stale.setStatus(ReviewStatus.REMOVED),
                concurrent -> concurrent.setGuideResponse("Thanks for coming!"));

        ReviewEntity after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(after.getGuideResponse()).isEqualTo("Thanks for coming!");
    }

    /**
     * Outer tx loads the review and applies {@code staleWrite}; before it commits, a nested {@code
     * REQUIRES_NEW} tx loads the same review, applies {@code concurrentWrite} and commits.
     */
    private void interleave(
            Consumer<ReviewEntity> staleWrite, Consumer<ReviewEntity> concurrentWrite) {
        newTx().executeWithoutResult(
                        outer -> {
                            ReviewEntity stale = reviews.findById(reviewId).orElseThrow();
                            assertThat(stale.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
                            staleWrite.accept(stale);
                            reviews.save(stale);
                            commitInNewTx(concurrentWrite);
                        });
    }

    /**
     * Loads the review in a separate {@code REQUIRES_NEW} transaction, applies the write, commits.
     */
    private void commitInNewTx(Consumer<ReviewEntity> write) {
        newTx().executeWithoutResult(
                        inner -> {
                            ReviewEntity fresh = reviews.findById(reviewId).orElseThrow();
                            write.accept(fresh);
                            reviews.save(fresh);
                        });
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
}
