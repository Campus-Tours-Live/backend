package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Deterministic concurrency test for the CTL-54 B5 fix: the per-guide advisory lock that {@link
 * AvailabilityService#rematerialize(UUID)} acquires at the start of its transaction, so two
 * concurrent rematerializes for the SAME guide (e.g. a guide-facing write racing the daily horizon
 * roll-forward job) SERIALIZE instead of interleaving their wholesale delete+insert and tripping
 * the {@code excl_guide_occurrence_no_overlap} GIST exclusion constraint (surfaced as an {@link
 * IllegalStateException}/500).
 *
 * <p><b>Why {@code @SpringBootTest} (not {@code @DataJpaTest} like the sibling availability
 * tests).</b> A real two-transaction race needs the production {@code @Transactional} proxy on
 * {@code rematerialize} to open a genuinely separate transaction (hence a separate DB connection)
 * per caller, so the {@code pg_advisory_xact_lock} is held for the whole method and actually blocks
 * the other caller. Under {@code @DataJpaTest} the service is {@code new}-constructed (no proxy)
 * and the whole test runs in ONE rolled-back transaction — the lock could never be observed to
 * serialize two callers. The full context boots offline via a mocked {@link JwtDecoder}, exactly as
 * {@code OpenApiDocsExportTest} does.
 *
 * <p><b>The held lock (thread A).</b> A helper thread opens its own JDBC transaction and acquires
 * the IDENTICAL per-guide advisory lock — {@code
 * pg_advisory_xact_lock(hashtextextended(guideId::text, 0))} — that {@code lockGuide} derives, then
 * HOLDS it behind a latch (the controllable pause). This stands in for a concurrent
 * rematerialize/horizon-job that has already entered its locked section. Driving the lock straight
 * from SQL (rather than pausing inside a real {@code rematerialize} via a mock) keeps the test
 * deterministic AND asserts the key derivation itself: if {@code lockGuide} computed a different
 * key, thread B below would NOT block.
 *
 * <p><b>Determinism (NOT an intermittent collision).</b> While A holds the lock, we submit thread B
 * — a real {@code rematerialize} for the same guide — and assert its future TIMES OUT (it is
 * blocked at lock acquisition). We then release A and assert B completes cleanly and only AFTER A
 * released. Without the lock, B never blocks: the timeout assertion fails immediately. The guide
 * starts with no occurrences, so nothing but the advisory lock can serialize the two writers.
 */
@SpringBootTest(
        properties = {
            // Pin the scheduled horizon job to a cron that will not fire during the test run, so it
            // never races the rematerialize we drive by hand.
            "availability.horizon-job.cron=0 0 0 1 1 ?"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityRematerializeLockConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private AvailabilityService availabilityService;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private GuideBookingSettingsRepository settingsRepo;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private DataSource dataSource;

    @Test
    void rematerialize_serializesConcurrentCallers_viaPerGuideAdvisoryLock() throws Exception {
        UUID guideId = seedGuideWithActiveRule();
        // Precondition: no materialized occurrences yet, so the only thing that can serialize the
        // two
        // concurrent writers is the advisory lock (no delete-time row locks to mask the race).
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId)).isEmpty();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicReference<Instant> holderReleasedAt = new AtomicReference<>();
        AtomicReference<Throwable> holderError = new AtomicReference<>();

        // Thread A: hold the identical per-guide advisory lock in its own transaction, then pause.
        Thread holder =
                new Thread(
                        () -> {
                            try (Connection conn = dataSource.getConnection()) {
                                conn.setAutoCommit(false);
                                try (PreparedStatement ps =
                                        conn.prepareStatement(
                                                "SELECT pg_advisory_xact_lock("
                                                        + "hashtextextended(CAST(? AS text), 0))")) {
                                    ps.setString(1, guideId.toString());
                                    try (ResultSet rs = ps.executeQuery()) {
                                        rs.next();
                                    }
                                }
                                lockHeld.countDown();
                                if (!releaseHolder.await(15, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "releaseHolder latch never fired");
                                }
                                holderReleasedAt.set(Instant.now());
                                conn.rollback(); // releases the transaction-scoped advisory lock.
                            } catch (Throwable t) {
                                holderError.set(t);
                                lockHeld.countDown();
                            }
                        },
                        "lock-holder-A");
        holder.start();

        assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();

        ExecutorService execB = Executors.newSingleThreadExecutor(namedThreadFactory("remat-B"));
        try {
            AtomicReference<Instant> bFinishedAt = new AtomicReference<>();
            Future<?> b =
                    execB.submit(
                            () -> {
                                availabilityService.rematerialize(guideId);
                                bFinishedAt.set(Instant.now());
                            });

            // Red signal: while A holds the per-guide lock, B must BLOCK at acquisition inside
            // rematerialize and make no progress. Without lockGuide, B runs to completion here.
            assertThatThrownBy(() -> b.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            // Release A; B must now complete cleanly — no GIST-exclusion IllegalStateException/500.
            releaseHolder.countDown();
            assertThatCode(() -> b.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();

            // Serialization signal: B's rematerialize finished only AFTER A released the lock.
            assertThat(bFinishedAt.get()).isAfterOrEqualTo(holderReleasedAt.get());
        } finally {
            releaseHolder.countDown();
            execB.shutdownNow();
            holder.join(5_000);
        }

        // B's rematerialize projected the seeded rule: the final materialized set is non-empty and
        // internally disjoint (the invariant the GIST constraint guards).
        List<GuideAvailabilityOccurrenceEntity> occ =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId);
        assertThat(occ).isNotEmpty();
        for (int i = 1; i < occ.size(); i++) {
            assertThat(occ.get(i).getDuringStartAt())
                    .isAfterOrEqualTo(occ.get(i - 1).getDuringEndAt());
        }
    }

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }

    private UUID seedGuideWithActiveRule() {
        UserEntity guideUser = new UserEntity();
        guideUser.setId(UUID.randomUUID());
        guideUser.setOidcSubject("lock-" + UUID.randomUUID());
        guideUser.setEmail("lock-" + UUID.randomUUID() + "@example.com");
        guideUser.setDisplayName("Lock Guide");
        guideUser.setAccountStatus(AccountStatus.ACTIVE);
        guideUser.setPreferredLanguage("en-US");
        guideUser.setTimezone("America/Los_Angeles");
        users.save(guideUser);

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(guide);

        GuideBookingSettingsEntity settings = new GuideBookingSettingsEntity();
        settings.setGuideId(guide.getId());
        settings.setTimezone(AvailabilityService.DEFAULT_TIMEZONE);
        settingsRepo.save(settings);

        // An active weekly rule effective from yesterday recurs on its weekday across the whole
        // rolling horizon, so rematerialize is guaranteed to project at least one occurrence.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guide.getId());
        rule.setDayOfWeek((short) (today.getDayOfWeek().getValue() % 7));
        rule.setStartLocal(LocalTime.of(9, 0));
        rule.setWindowMin(60);
        rule.setTimezone(AvailabilityService.DEFAULT_TIMEZONE);
        rule.setEffectiveFrom(today.minusDays(1));
        rule.setEffectiveTo(null);
        rule.setActive(true);
        rules.save(rule);

        return guide.getId();
    }
}
