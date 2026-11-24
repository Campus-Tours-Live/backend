package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CTL-139: two reviews of the same guide/offering written concurrently must both count.
 *
 * <p>Thread A creates a review and holds its transaction open after the recompute (so it holds the
 * guide and offering row locks). Thread B then creates a second review; it must wait for A, and
 * once A commits, B's recompute must see A's review. Under READ COMMITTED, a single {@code UPDATE …
 * SET x = (SELECT … FROM reviews)} that waited on the row lock re-applies with its ORIGINAL
 * statement snapshot, so without locking the row first B's recompute misses A's review and the
 * stored aggregate undercounts (1 review instead of 2).
 *
 * <p>Latches, no sleeps; class-level {@code NOT_SUPPORTED} so each thread commits for real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReviewService.class, ReviewWriteFixtures.class})
@Timeout(60)
class ReviewAggregateConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired ReviewService service;
    @Autowired ReviewWriteFixtures fixtures;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private ExecutorService pool;

    @BeforeEach
    void startPool() {
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void concurrentReviewsOfOneGuide_bothCountInGuideAndOfferingAggregates() throws Exception {
        ReviewWriteFixtures.ReviewableGraph g = fixtures.seedReviewableBookings(2);
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<ReviewResponse> first =
                pool.submit(
                        () ->
                                newTx().execute(
                                                status -> {
                                                    ReviewResponse r =
                                                            service.createReview(
                                                                    g.participants().get(0),
                                                                    g.bookingIds().get(0),
                                                                    rating(5));
                                                    firstWritten.countDown();
                                                    await(releaseFirst);
                                                    return r;
                                                }));
        await(firstWritten);

        Future<ReviewResponse> second =
                pool.submit(
                        () ->
                                service.createReview(
                                        g.participants().get(1), g.bookingIds().get(1), rating(3)));
        // The first review's transaction still holds the aggregate rows, so the second must wait.
        assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseFirst.countDown();
        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertAggregate("guide_profiles", g.guideId(), 2, "4.00");
        assertAggregate("tour_offerings", g.offeringId(), 2, "4.00");
    }

    private void assertAggregate(String table, UUID id, int count, String avg) {
        var row =
                jdbc.queryForMap(
                        "SELECT review_count, avg_rating FROM " + table + " WHERE id = ?", id);
        assertThat(((Number) row.get("review_count")).intValue())
                .as(table + ".review_count")
                .isEqualTo(count);
        assertThat((BigDecimal) row.get("avg_rating"))
                .as(table + ".avg_rating")
                .isEqualByComparingTo(avg);
    }

    private static CreateReviewRequest rating(int overall) {
        return new CreateReviewRequest(overall, null, null, null, null, null, null);
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", e);
        }
    }
}
