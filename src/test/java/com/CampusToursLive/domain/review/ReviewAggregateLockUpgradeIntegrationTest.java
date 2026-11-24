package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CTL-139 review follow-up (#92): two reviews of one guide that are BOTH inserted before either
 * transaction takes the aggregate row lock must both commit, with no deadlock.
 *
 * <p>{@code createReview} inserts the review ({@code saveAndFlush}) before recomputing, and that
 * insert takes a foreign-key {@code KEY SHARE} lock on the parent {@code guide_profiles} and {@code
 * tour_offerings} rows. A {@code FOR UPDATE} recompute lock conflicts with the other transaction's
 * {@code KEY SHARE}, so two writers in this schedule wait on each other and Postgres aborts one
 * with a deadlock. {@code FOR NO KEY UPDATE} does not conflict with {@code KEY SHARE} but still
 * serializes the two recomputes, so both commit and the aggregate counts both reviews.
 *
 * <p>The schedule is forced with the spied {@code recomputeRatingAggregate}: the first writer (its
 * review already flushed) waits there until the second writer has flushed its own review and
 * reached the same point; then both take the lock. Latches, no sleeps.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReviewService.class, ReviewWriteFixtures.class})
@Timeout(60)
class ReviewAggregateLockUpgradeIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired ReviewService service;
    @Autowired ReviewWriteFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean GuideProfileRepository guides;

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
    void bothReviewsInsertedBeforeEitherLocks_bothCommit_andBothCount() throws Exception {
        ReviewWriteFixtures.ReviewableGraph g = fixtures.seedReviewableBookings(2);
        AtomicInteger recomputes = new AtomicInteger();
        CountDownLatch firstAtRecompute = new CountDownLatch(1);
        CountDownLatch secondInserted = new CountDownLatch(1);
        // A spy of a Spring Data repository can't callRealMethod; its default answer forwards to
        // the real repository (which runs the default method: lock, then recompute).
        Answer<?> forwardToReal =
                mockingDetails(guides).getMockCreationSettings().getDefaultAnswer();
        doAnswer(
                        invocation -> {
                            int n = recomputes.incrementAndGet();
                            if (n == 1) {
                                firstAtRecompute.countDown();
                                await(secondInserted);
                            } else if (n == 2) {
                                secondInserted.countDown();
                            }
                            return forwardToReal.answer(invocation);
                        })
                .when(guides)
                .recomputeRatingAggregate(any());

        Future<ReviewResponse> first =
                pool.submit(
                        () ->
                                service.createReview(
                                        g.participants().get(0), g.bookingIds().get(0), rating(5)));
        await(firstAtRecompute);
        Future<ReviewResponse> second =
                pool.submit(
                        () ->
                                service.createReview(
                                        g.participants().get(1), g.bookingIds().get(1), rating(3)));

        // Neither may fail: under FOR UPDATE one of these throws a deadlock-aborted exception.
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
