package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceEntity;
import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
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
import com.CampusToursLive.web.dto.CartItemResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Concurrency proof for add-to-cart idempotency (CTL-83 review follow-up), against real PostgreSQL.
 *
 * <p>Same style as {@code OidcIdentityLockIntegrationTest}: independently committing transactions
 * on separate threads, coordinated with latches (no sleeps), so the result never depends on timing.
 * Thread A adds an item inside a transaction it holds open; a concurrent identical add from thread
 * B must BLOCK while A is open (only the cart lock can stop it) and, once A commits, return A's row
 * instead of inserting a duplicate. Without the lock, B neither blocks nor sees A's uncommitted
 * row, so it inserts a second DRAFT and this test fails.
 *
 * <p>Class-level {@code NOT_SUPPORTED} turns off {@code @DataJpaTest}'s rollback-only test
 * transaction, since each thread needs its own committing transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BookingService.class, PostgresParticipantCartLock.class})
class CartConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired BookingService service;
    @Autowired ParticipantCartLock cartLock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;
    @Autowired GuideAvailabilityOccurrenceRepository occurrences;

    private UserEntity participant;
    private TourOfferingEntity offering;
    private ExecutorService pool;

    @BeforeEach
    void seedGraph() {
        pool = Executors.newFixedThreadPool(2);
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

        GuideAvailabilityOccurrenceEntity occ = new GuideAvailabilityOccurrenceEntity();
        occ.setId(UUID.randomUUID());
        occ.setGuideId(guide.getId());
        occ.setDuringStartAt(Instant.now().minus(1, ChronoUnit.DAYS));
        occ.setDuringEndAt(Instant.now().plus(60, ChronoUnit.DAYS));
        occ.setGeneratedAt(Instant.now());
        occurrences.saveAndFlush(occ);
    }

    @AfterEach
    void shutDownPool() {
        pool.shutdownNow();
    }

    @Test
    void concurrentSameItemAdds_serialize_andTheSecondReturnsTheFirstsRow() throws Exception {
        CreateBookingRequest req = request(Instant.now().plus(3, ChronoUnit.DAYS));
        CountDownLatch firstAdded = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<CartItemResponse> first =
                pool.submit(
                        () ->
                                newTx().execute(
                                                status -> {
                                                    CartItemResponse r =
                                                            service.addCartItem(participant, req);
                                                    firstAdded.countDown();
                                                    await(releaseFirst);
                                                    return r;
                                                }));
        await(firstAdded);

        Future<CartItemResponse> second = pool.submit(() -> service.addCartItem(participant, req));
        // While the first add's transaction is open, the identical add must wait on the cart lock.
        assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseFirst.countDown();
        CartItemResponse a = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CartItemResponse b = second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(b.id()).isEqualTo(a.id());
        assertThat(
                        bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                                participant.getId(), BookingStatus.DRAFT))
                .extracting(BookingEntity::getId)
                .containsExactly(UUID.fromString(a.id()));
    }

    @Test
    void anotherParticipantsAdd_isNotBlockedByAHeldCartLock() throws Exception {
        UserEntity other = users.save(user("Olive Other"));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder =
                pool.submit(
                        () ->
                                newTx().executeWithoutResult(
                                                status -> {
                                                    cartLock.acquire(participant.getId());
                                                    locked.countDown();
                                                    await(release);
                                                }));
        await(locked);
        try {
            CartItemResponse added =
                    pool.submit(
                                    () ->
                                            service.addCartItem(
                                                    other,
                                                    request(
                                                            Instant.now()
                                                                    .plus(4, ChronoUnit.DAYS))))
                            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(added.status()).isEqualTo("DRAFT");
        } finally {
            release.countDown();
            holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void acquire_outsideATransaction_isRejected() {
        assertThatThrownBy(() -> cartLock.acquire(participant.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an active transaction");
    }

    private CreateBookingRequest request(Instant start) {
        return new CreateBookingRequest(
                offering.getId().toString(),
                start.truncatedTo(ChronoUnit.MINUTES).toString(),
                null);
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
