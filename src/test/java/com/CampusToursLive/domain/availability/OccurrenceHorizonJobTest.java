package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link OccurrenceHorizonJob} (CTL-54 Task 4) against a REAL PostgreSQL
 * (Testcontainers) — the only way to exercise the real {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * claim and genuine {@code REQUIRES_NEW} transaction boundaries (needed for the failure-isolation
 * proof below).
 *
 * <p>The default {@code @DataJpaTest} test-transaction wrapping is turned OFF
 * ({@code @Transactional(propagation = NOT_SUPPORTED)}): {@link GuideHorizonClaimService}'s {@code
 * REQUIRES_NEW} claim query needs to see guides seeded in {@code @BeforeEach} as ALREADY COMMITTED
 * (a still-open outer test transaction would hide them from the separate DB session {@code
 * REQUIRES_NEW} opens). {@link AvailabilityService} is wired here with a package-private, test-only
 * {@link MutableClock} (same test-seam constructor {@code AvailabilityServiceIntegrationTest} uses,
 * but mutable so a single test method can advance "today" mid-test and prove the horizon rolls
 * forward without rebuilding the bean graph).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({GuideHorizonClaimService.class, OccurrenceHorizonJob.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OccurrenceHorizonJobTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final String LA = "America/Los_Angeles";
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 1);
    private static final Instant FIXED_INSTANT =
            FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant();

    /**
     * Test-only mutable {@link Clock}: same instance stays wired into the {@code
     * AvailabilityService} bean for the whole test, but {@link #advanceDays(long)}/{@link
     * #reset(Instant)} let a test move "now" without reconstructing any bean.
     */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceDays(long days) {
            instant = instant.plus(days, ChronoUnit.DAYS);
        }

        void reset(Instant newInstant) {
            instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        MutableClock mutableClock() {
            return new MutableClock(FIXED_INSTANT);
        }

        @Bean
        AvailabilityService availabilityService(
                GuideAvailabilityRuleRepository rules,
                AvailabilityExceptionRepository exceptions,
                GuideAvailabilityOccurrenceRepository occurrences,
                GuideAvailabilityDstNoticeRepository dstNotices,
                GuideBookingSettingsRepository settings,
                jakarta.persistence.EntityManager entityManager,
                MutableClock clock) {
            return new AvailabilityService(
                    rules, exceptions, occurrences, dstNotices, settings, entityManager, clock);
        }
    }

    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;
    @Autowired private OccurrenceHorizonJob job;
    @Autowired private MutableClock clock;

    @MockitoSpyBean private GuideHorizonClaimService claimServiceSpy;

    private UUID universityId;

    @BeforeEach
    void setUp() {
        clock.reset(FIXED_INSTANT);
        Mockito.reset(claimServiceSpy);
        universityId =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow()
                        .getId();
    }

    @Test
    void rollHorizonForward_extendsCoverageAsClockAdvances() {
        UUID guideId = seedGuideWithWeeklyRule();

        job.rollHorizonForward();
        Instant expectedMaxEnd1 = expectedMaxEnd(guideId, FIXED_TODAY);
        Instant actualMaxEnd1 = actualMaxEnd(guideId);
        assertThat(actualMaxEnd1).isEqualTo(expectedMaxEnd1);

        clock.advanceDays(30);
        LocalDate day2 = FIXED_TODAY.plusDays(30);

        job.rollHorizonForward();
        Instant expectedMaxEnd2 = expectedMaxEnd(guideId, day2);
        Instant actualMaxEnd2 = actualMaxEnd(guideId);

        // The core roll-forward proof: re-running the job at a later "now" moves the far edge of
        // coverage forward (matches the pure projection re-evaluated at the new horizon).
        assertThat(actualMaxEnd2).isEqualTo(expectedMaxEnd2);
        assertThat(actualMaxEnd2).isAfter(actualMaxEnd1);
    }

    @Test
    void rollHorizonForward_materializesMultipleGuidesInOneRun() {
        UUID guideA = seedGuideWithWeeklyRule();
        UUID guideB = seedGuideWithWeeklyRule();

        job.rollHorizonForward();

        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideA)).isNotEmpty();
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideB)).isNotEmpty();
    }

    @Test
    void rollHorizonForward_isolatesOneGuidesFailureFromAnother() {
        UUID guideA = seedGuideWithWeeklyRule();
        UUID guideB = seedGuideWithWeeklyRule();

        doThrow(new IllegalStateException("induced failure for guideA"))
                .when(claimServiceSpy)
                .claimAndRematerialize(eq(guideA));

        // A bad guide must never abort the batch: the job itself must not throw...
        assertThatCode(() -> job.rollHorizonForward()).doesNotThrowAnyException();

        // ...and the OTHER guide must still have been materialized.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideA)).isEmpty();
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideB)).isNotEmpty();
    }

    @Test
    void rollHorizonForward_isIdempotentAcrossRuns() {
        UUID guideId = seedGuideWithWeeklyRule();

        job.rollHorizonForward();
        List<Instant> firstRun =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .map(GuideAvailabilityOccurrenceEntity::getDuringStartAt)
                        .toList();
        assertThat(firstRun).isNotEmpty();

        // Re-running at the SAME clock must leave the identical set: no duplicates, no GIST
        // violation (rematerialize's wholesale delete+insert is idempotent; the job adds no state
        // of its own beyond the per-run claim).
        assertThatCode(() -> job.rollHorizonForward()).doesNotThrowAnyException();

        List<Instant> secondRun =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .map(GuideAvailabilityOccurrenceEntity::getDuringStartAt)
                        .toList();
        assertThat(secondRun).isEqualTo(firstRun);
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private Instant expectedMaxEnd(UUID guideId, LocalDate today) {
        AvailabilityHorizon horizon =
                new AvailabilityHorizon(today, today.plusDays(AvailabilityService.HORIZON_DAYS));
        ProjectionResult expected =
                AvailabilityProjection.project(rules.findByGuideId(guideId), List.of(), horizon);
        return expected.intervals().stream()
                .map(AvailabilityInterval::endAt)
                .max(Instant::compareTo)
                .orElseThrow();
    }

    private Instant actualMaxEnd(UUID guideId) {
        return occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                .map(GuideAvailabilityOccurrenceEntity::getDuringEndAt)
                .max(Instant::compareTo)
                .orElseThrow();
    }

    /** A fresh guide with one weekly rule spanning the whole horizon (no {@code effectiveTo}). */
    private UUID seedGuideWithWeeklyRule() {
        UserEntity guideUser = users.save(user("Jane Guide " + UUID.randomUUID()));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setUniversityId(universityId);
        guide.setMajor("Computer Science");
        guide.setApplicationStatus(GuideApplicationStatus.APPROVED);
        guides.save(guide);
        UUID guideId = guide.getId();

        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guideId);
        rule.setDayOfWeek(
                (short) (FIXED_TODAY.getDayOfWeek().getValue() % 7)); // matches FIXED_TODAY
        rule.setStartLocal(LocalTime.of(9, 0));
        rule.setWindowMin(60);
        rule.setTimezone(LA);
        rule.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        rule.setEffectiveTo(null);
        rule.setActive(true);
        rules.save(rule);

        return guideId;
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("it-" + UUID.randomUUID());
        u.setEmail("it-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone(LA);
        return u;
    }
}
