package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
 * CTL-141 auto-complete against real PostgreSQL: which bookings are due (status + 24h default
 * grace), what a completion writes, batching, and that a row another transaction holds is skipped
 * ({@code SKIP LOCKED}) instead of blocking the sweep. Class-level {@code NOT_SUPPORTED} so seeded
 * rows are really committed (needed for the cross-transaction lock case); each test starts from an
 * empty bookings table.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    BookingCompletionService.class,
    BookingCompletionServiceIntegrationTest.FixedClockConfig.class
})
@Timeout(60)
class BookingCompletionServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired BookingCompletionService completion;
    @Autowired BookingRepository bookings;
    @Autowired BookingStatusHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private UserEntity participant;
    private TourOfferingEntity offering;
    private ExecutorService pool;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM booking_status_history");
        jdbc.update("DELETE FROM bookings");
        pool = Executors.newSingleThreadExecutor();

        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        participant = users.save(user("Pat Participant"));
        UserEntity guideUser = users.save(user("Jane Guide"));
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(guideUser.getId());
        g.setStatus(GuideStatus.VERIFIED);
        GuideProfileEntity guide = guides.save(g);

        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setUniversityId(university.getId());
        o.setTitle("Campus Walk");
        o.setSlug("campus-walk-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        o.setStatus(TourStatus.ACTIVE);
        offering = offerings.save(o);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void completesConfirmedAndInProgressPastTheGrace_withASystemHistoryRow() {
        BookingEntity confirmed = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofHours(25)));
        BookingEntity inProgress =
                booking(BookingStatus.IN_PROGRESS, endedAgo(Duration.ofHours(30)));

        assertThat(completion.completeEndedBatch(100)).isEqualTo(2);

        for (BookingEntity b : List.of(confirmed, inProgress)) {
            BookingEntity after = bookings.findById(b.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(after.getCompletedAt()).isEqualTo(NOW);
            assertThat(history.findByBookingIdOrderByCreatedAtAsc(b.getId()))
                    .singleElement()
                    .satisfies(
                            h -> {
                                assertThat(h.getPreviousStatus()).isEqualTo(b.getStatus());
                                assertThat(h.getNewStatus()).isEqualTo(BookingStatus.COMPLETED);
                                assertThat(h.getActorType()).isEqualTo(BookingActor.SYSTEM);
                                assertThat(h.getReasonCode())
                                        .isEqualTo(BookingCompletionService.REASON);
                            });
        }
    }

    @Test
    void leavesBookingsInsideTheGrace_terminalOnes_andFutureOnesAlone() {
        BookingEntity withinGrace =
                booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofHours(23)));
        BookingEntity noShow =
                booking(BookingStatus.PARTICIPANT_NO_SHOW, endedAgo(Duration.ofDays(3)));
        BookingEntity cancelled =
                booking(BookingStatus.CANCELLED_BY_GUIDE, endedAgo(Duration.ofDays(4)));
        BookingEntity upcoming = booking(BookingStatus.CONFIRMED, NOW.plus(Duration.ofDays(2)));

        assertThat(completion.completeEndedBatch(100)).isZero();

        assertThat(statusOf(withinGrace)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(statusOf(noShow)).isEqualTo(BookingStatus.PARTICIPANT_NO_SHOW);
        assertThat(statusOf(cancelled)).isEqualTo(BookingStatus.CANCELLED_BY_GUIDE);
        assertThat(statusOf(upcoming)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void completesInBoundedBatches_oldestFirst_andIsIdempotent() {
        BookingEntity oldest = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofDays(5)));
        BookingEntity middle = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofDays(4)));
        BookingEntity newest = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofDays(3)));

        assertThat(completion.completeEndedBatch(2)).isEqualTo(2);
        assertThat(statusOf(oldest)).isEqualTo(BookingStatus.COMPLETED);
        assertThat(statusOf(middle)).isEqualTo(BookingStatus.COMPLETED);
        assertThat(statusOf(newest)).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(completion.completeEndedBatch(2)).isEqualTo(1);
        assertThat(completion.completeEndedBatch(2)).isZero(); // re-run is a no-op
        assertThat(history.findByBookingIdOrderByCreatedAtAsc(newest.getId())).hasSize(1);
    }

    @Test
    void aRowLockedByAnotherTransaction_isSkipped_notWaitedOn() throws Exception {
        BookingEntity locked = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofDays(2)));
        BookingEntity free = booking(BookingStatus.CONFIRMED, endedAgo(Duration.ofDays(3)));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Another transaction (e.g. a guide action in flight) holds a row lock on one due booking.
        Future<?> holder =
                pool.submit(
                        () ->
                                newTx().executeWithoutResult(
                                                status -> {
                                                    jdbc.queryForList(
                                                            "SELECT id FROM bookings WHERE id = ?"
                                                                    + " FOR UPDATE",
                                                            locked.getId());
                                                    held.countDown();
                                                    await(release);
                                                }));
        await(held);
        try {
            // Returns promptly (the class @Timeout would fail a blocked call) and only takes the
            // unlocked row.
            assertThat(completion.completeEndedBatch(100)).isEqualTo(1);
            assertThat(statusOf(free)).isEqualTo(BookingStatus.COMPLETED);
            assertThat(statusOf(locked)).isEqualTo(BookingStatus.CONFIRMED);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }

        // Once the other transaction is done, the next tick picks the skipped row up.
        assertThat(completion.completeEndedBatch(100)).isEqualTo(1);
        assertThat(statusOf(locked)).isEqualTo(BookingStatus.COMPLETED);
    }

    private BookingStatus statusOf(BookingEntity b) {
        return bookings.findById(b.getId()).orElseThrow().getStatus();
    }

    private static Instant endedAgo(Duration ago) {
        return NOW.minus(ago);
    }

    /** A committed 60-minute booking with the given status, ending at {@code end}. */
    private BookingEntity booking(BookingStatus status, Instant end) {
        Instant start = end.minus(60, ChronoUnit.MINUTES);
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-AC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(offering.getGuideId());
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(status);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(end);
        // Distinct reserved windows so the guide no-overlap exclusion constraint never trips.
        b.setReservedStartAt(start);
        b.setReservedEndAt(end.plus(15, ChronoUnit.MINUTES));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(5000L);
        b.setCurrency("USD");
        return bookings.saveAndFlush(b);
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", e);
        }
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("it-" + UUID.randomUUID());
        u.setEmail("it-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }
}
