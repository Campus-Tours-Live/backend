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
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * Deterministic concurrency test for the CTL-54 settings first-write race fix: {@link
 * AvailabilityWriteService}'s {@code getOrCreateSettings} provisions the guide's default {@code
 * guide_booking_settings} row via an idempotent {@code INSERT ... ON CONFLICT (guide_id) DO
 * NOTHING} upsert, so two concurrent first-writes for the SAME guide both succeed instead of the
 * loser tripping the {@code guide_id} primary key and surfacing a 500.
 *
 * <p><b>Why {@code @SpringBootTest} (not {@code @DataJpaTest}).</b> A real two-transaction race
 * needs the production {@code @Transactional} proxy on the write path so thread B opens a genuinely
 * separate transaction (and connection) whose first-write insert blocks on the unique index held by
 * thread A's uncommitted insert -- exactly the interleaving that used to 500. Under
 * {@code @DataJpaTest} everything runs in one rolled-back transaction, so the race cannot occur.
 * The full context boots offline via a mocked {@link JwtDecoder}, exactly as the sibling
 * rematerialize-lock concurrency test does.
 *
 * <p><b>Determinism.</b> Thread A inserts the settings row in its own JDBC transaction and HOLDS it
 * uncommitted behind a latch. While A holds it, thread B calls {@code getSettings} (which funnels
 * through {@code getOrCreateSettings}); B's find sees no row (A uncommitted), then B's upsert
 * BLOCKS on the unique index until A commits -- we assert B times out. We then commit A and assert
 * B completes cleanly: with {@code ON CONFLICT DO NOTHING} B's insert becomes a no-op and B reads
 * the winner's row. Under the old plain insert, B's insert would raise a duplicate-key violation
 * the moment A committed -- so B's future would throw and this test would fail.
 */
@SpringBootTest(
        properties = {
            // Pin the scheduled horizon job to a cron that will not fire during the test run.
            "availability.horizon-job.cron=0 0 0 1 1 ?"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideBookingSettingsUpsertConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private AvailabilityWriteService writeService;
    @Autowired private GuideBookingSettingsRepository settingsRepo;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private DataSource dataSource;

    @Test
    void firstWriteSettings_concurrentProvision_bothSucceed_noPrimaryKey500() throws Exception {
        UUID guideUserId = seedGuideWithoutSettings();
        UUID guideId = guides.findByUserId(guideUserId).orElseThrow().getId();
        // Precondition: the guide has NO settings row yet, so both callers below take the
        // first-write (insert) branch.
        assertThat(settingsRepo.findByGuideId(guideId)).isEmpty();

        CountDownLatch insertHeld = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();

        // Thread A: insert the guide's settings row in its own transaction and HOLD it uncommitted.
        Thread holder =
                new Thread(
                        () -> {
                            try (Connection conn = dataSource.getConnection()) {
                                conn.setAutoCommit(false);
                                try (PreparedStatement ps =
                                        conn.prepareStatement(
                                                "INSERT INTO guide_booking_settings (guide_id)"
                                                        + " VALUES (?)")) {
                                    ps.setObject(1, guideId);
                                    ps.executeUpdate();
                                }
                                insertHeld.countDown();
                                if (!releaseHolder.await(15, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "releaseHolder latch never fired");
                                }
                                conn.commit(); // A wins: its row becomes the committed one.
                            } catch (Throwable t) {
                                holderError.set(t);
                                insertHeld.countDown();
                            }
                        },
                        "settings-holder-A");
        holder.start();

        assertThat(insertHeld.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();

        ExecutorService execB = Executors.newSingleThreadExecutor(namedThreadFactory("settings-B"));
        try {
            Future<GuideBookingSettingsResponse> b =
                    execB.submit(() -> writeService.getSettings(actingUser(guideUserId)));

            // Red signal: while A holds the uncommitted insert, B's first-write upsert BLOCKS on
            // the
            // unique index and makes no progress.
            assertThatThrownBy(() -> b.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            // Commit A; B must now complete cleanly -- no duplicate-key 500. With ON CONFLICT DO
            // NOTHING B's insert is a no-op and B reads A's committed row.
            releaseHolder.countDown();
            assertThatCode(() -> assertThat(b.get(10, TimeUnit.SECONDS)).isNotNull())
                    .doesNotThrowAnyException();
        } finally {
            releaseHolder.countDown();
            execB.shutdownNow();
            holder.join(5_000);
        }

        // Exactly one settings row exists for the guide (A's), and B did not create a duplicate.
        assertThat(settingsRepo.findByGuideId(guideId)).isPresent();
    }

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }

    private UUID seedGuideWithoutSettings() {
        UserEntity guideUser = new UserEntity();
        guideUser.setId(UUID.randomUUID());
        guideUser.setOidcSubject("gset-" + UUID.randomUUID());
        guideUser.setEmail("gset-" + UUID.randomUUID() + "@example.com");
        guideUser.setDisplayName("Settings Guide");
        guideUser.setAccountStatus(AccountStatus.ACTIVE);
        guideUser.setPreferredLanguage("en-US");
        guideUser.setTimezone("America/Los_Angeles");
        users.save(guideUser);

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(guide);

        return guideUser.getId();
    }

    private static UserEntity actingUser(UUID userId) {
        UserEntity u = new UserEntity();
        u.setId(userId);
        return u;
    }
}
