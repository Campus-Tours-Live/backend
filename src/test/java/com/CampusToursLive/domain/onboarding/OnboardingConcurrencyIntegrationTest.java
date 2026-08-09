package com.CampusToursLive.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.CampusToursLive.domain.audit.AuditLogEntity;
import com.CampusToursLive.domain.audit.AuditLogRepository;
import com.CampusToursLive.domain.audit.AuditWriter;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.security.OidcIdentity;
import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers (real PostgreSQL, full Spring context) concurrency + write-skew + audit-durability
 * suite for {@link OnboardingService} — CTL-97 Core-B Task 10. Proves, against a real database
 * rather than a mock, the invariants Tasks 1-9 built: the per-identity {@code
 * pg_advisory_xact_lock} genuinely serializes concurrent onboarding attempts for the SAME OIDC
 * identity, the GUIDE&harr;PARENT exclusion (I13) survives write-skew races, and the in-transaction
 * audit write ({@link AuditWriter}) is rolled back along with everything else on a mid-transaction
 * failure.
 *
 * <p><b>Naming.</b> This repo has no failsafe plugin, so a {@code *IT.java} class is silently
 * skipped by {@code ./mvnw verify}'s surefire-only run. Named {@code *IntegrationTest} (not the
 * {@code OnboardingConcurrencyIT} the Task 10 planning doc used) so surefire actually executes it.
 *
 * <p><b>Concurrency pattern.</b> Each race uses a {@code CountDownLatch(2)} start gate so both
 * competing calls are released together (see {@link #race}), then each runs a REAL
 * {@code @Transactional} service call on its own thread/connection — never a raw SQL lock-hold
 * standing in for the real call — and the test asserts the actual post-state. Because {@link
 * com.CampusToursLive.security.OidcIdentityLock} fully serializes the whole critical section for a
 * given identity, the two threads never interleave inside it regardless of which one the JVM
 * schedules "first" — only their DB-transaction commit order decides the winner, so the assertions
 * below check the REAL final state rather than assume a specific winner.
 *
 * <p><b>Case 6 (same transaction/connection).</b> Cases 1 and 2 already prove this: {@code
 * pg_advisory_xact_lock} is transaction-scoped, so a race that gets serialized (as those two
 * genuinely are, against a real Postgres) is itself the proof that the lock and every subsequent
 * insert in the same call ride the SAME transaction/connection — were the lock acquired on a
 * different connection than the inserts, the loser's read of "role already granted" could never be
 * guaranteed to see the winner's write before its own conflicting insert. No separate test is added
 * for this — see {@code AvailabilityRematerializeLockConcurrencyIntegrationTest} and {@code
 * OidcIdentityLockIntegrationTest} for the dedicated raw-SQL lock-scope proofs this suite builds
 * on.
 *
 * <p><b>Case 4 (now consistent, CTL-97 Minor-3).</b> {@link ParticipantService#updateProfile} and
 * {@link OnboardingService} enforce the SAME I13 exclusion and now both throw the SAME {@link
 * ConflictException} ({@code ROLE_NOT_ELIGIBLE}, HTTP 409) regardless of which of the two racing
 * calls loses: {@code ParticipantService.updateProfile}'s own inline check (hit on a direct
 * PATCH&rarr;PARENT) was aligned to {@code OnboardingService.onboardGuide}'s pre-check, which
 * previously threw a different exception type ({@code ValidationException}, HTTP 422) for the same
 * rule. This test asserts the 409 on whichever branch actually occurred (see {@code
 * participantPatchToParent_concurrentWithOnboardGuide_i13ExclusionUpheld}) rather than force one
 * ordering.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OnboardingConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private OnboardingService onboardingService;
    @Autowired private ParticipantService participantService;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private ParticipantProfileRepository participantProfiles;
    @Autowired private UniversityRepository universities;
    @Autowired private AuditLogRepository auditLogs;

    // Case 5 only: a spy so the FIRST audit write (ACCOUNT_PROVISIONED) executes for real while the
    // SECOND (ROLE_ACQUIRED) is stubbed to throw, forcing a genuine mid-transaction rollback.
    @MockitoSpyBean private AuditWriter auditWriter;

    private static final String ISSUER = "https://accounts.google.com";
    private static final long RACE_TIMEOUT_SECONDS = 20;

    @AfterEach
    void resetAuditWriterSpy() {
        // The spy is a singleton Spring bean shared across every test method in this class; reset
        // it unconditionally so case 5's stub never leaks into any other test's onboardGuide call.
        reset(auditWriter);
    }

    // ---- fixtures (mirrors OnboardingServiceIntegrationTest) -------------------------------

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("name", "Test User")
                .build();
    }

    private UUID seedUniversity() {
        UniversityEntity u = new UniversityEntity();
        u.setId(UUID.randomUUID());
        u.setSlug("onboarding-concurrency-test-uni-" + UUID.randomUUID());
        u.setName("Onboarding Concurrency Test University");
        u.setCity("Testville");
        u.setTimezone("America/Los_Angeles");
        u.setStatus(UniversityStatus.ACTIVE);
        universities.saveAndFlush(u);
        return u.getId();
    }

    private static GuideOnboardingRequest guideRequest(UUID universityId) {
        return new GuideOnboardingRequest(
                "Maya",
                "Chen",
                universityId.toString(),
                "Marine Biology",
                "2027",
                "Third-year student and campus tour lead.",
                List.of("en-US"),
                List.of("DORM_HOUSING"),
                "maya.chen@ncu.edu",
                "Bachelor's Degree",
                2023);
    }

    private static ParticipantOnboardingRequest participantRequest(String participantType) {
        return new ParticipantOnboardingRequest(
                "Sam",
                "Rivera",
                "Sam Rivera",
                participantType,
                "High school senior",
                "Computer Science",
                List.of(),
                List.of(),
                "en-US",
                "America/New_York",
                null);
    }

    private static ParticipantProfileUpdateRequest patchToParentRequest() {
        return new ParticipantProfileUpdateRequest(
                null, null, null, "PARENT", null, null, null, null, null, null, null);
    }

    // ---- race harness: two REAL service calls, released together off one start gate --------

    private record RaceOutcome<T>(T value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    private record Race<A, B>(RaceOutcome<A> first, RaceOutcome<B> second) {}

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }

    private static <T> RaceOutcome<T> runGated(CountDownLatch startGate, Callable<T> work) {
        startGate.countDown();
        try {
            if (!startGate.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("race start gate never released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting at race start gate", e);
        }
        try {
            return new RaceOutcome<>(work.call(), null);
        } catch (Throwable t) {
            return new RaceOutcome<>(null, t);
        }
    }

    private static <T> RaceOutcome<T> await(Future<RaceOutcome<T>> future) {
        try {
            return future.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("race participant did not complete in time", e);
        }
    }

    /**
     * Runs {@code first} and {@code second} concurrently on two dedicated threads, each held at a
     * shared {@link CountDownLatch} start gate so both are released together — genuinely exercising
     * the identity lock's serialization rather than two calls that happen to run sequentially.
     * Neither call's exception propagates out of this method: both are captured into their {@link
     * RaceOutcome} so the caller can inspect winner AND loser.
     */
    private <A, B> Race<A, B> race(Callable<A> first, Callable<B> second) {
        CountDownLatch startGate = new CountDownLatch(2);
        ExecutorService pool =
                Executors.newFixedThreadPool(2, namedThreadFactory("onboarding-race"));
        try {
            Future<RaceOutcome<A>> f1 = pool.submit(() -> runGated(startGate, first));
            Future<RaceOutcome<B>> f2 = pool.submit(() -> runGated(startGate, second));
            return new Race<>(await(f1), await(f2));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- case 1: two identical onboardGuide, same identity -> exactly one wins ------------

    @Test
    void onboardGuide_concurrentDuplicate_exactlyOneWins_totalsMatchWinnerOnly() {
        UUID universityId = seedUniversity();
        String subject = "guide-dup-race-" + UUID.randomUUID();
        Jwt token = jwt(subject);

        Race<OnboardingResponse, OnboardingResponse> race =
                race(
                        () -> onboardingService.onboardGuide(token, guideRequest(universityId)),
                        () -> onboardingService.onboardGuide(token, guideRequest(universityId)));

        List<RaceOutcome<OnboardingResponse>> outcomes = List.of(race.first(), race.second());
        List<RaceOutcome<OnboardingResponse>> winners =
                outcomes.stream().filter(RaceOutcome::succeeded).toList();
        List<RaceOutcome<OnboardingResponse>> losers =
                outcomes.stream().filter(o -> !o.succeeded()).toList();

        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);
        assertThat(winners.get(0).value().acquiredRole()).isEqualTo(UserRole.GUIDE);

        Throwable loserError = losers.get(0).error();
        assertThat(loserError).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) loserError).code()).isEqualTo("ROLE_ALREADY_GRANTED");

        // Final totals prove the loser added ZERO rows: these are the WINNER's totals, and only the
        // winner's -- one user, one guide profile, one GUIDE grant, one of each audit action.
        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        assertThat(guideProfiles.findByUserId(userId)).isPresent();
        assertThat(userRoles.findByUserId(userId))
                .extracting(UserRoleEntity::getRole)
                .containsExactly(UserRole.GUIDE);
        List<AuditLogEntity> auditRows =
                auditLogs.findByTargetTypeAndTargetId("user", userId.toString());
        assertThat(auditRows)
                .extracting(AuditLogEntity::getAction)
                .containsExactlyInAnyOrder("ACCOUNT_PROVISIONED", "ROLE_ACQUIRED");
    }

    // ---- case 2: PARENT participant vs GUIDE, same identity -> write-skew prevented -------

    @Test
    void onboardParticipantParent_concurrentWithOnboardGuide_writeSkewPrevented() {
        UUID universityId = seedUniversity();
        String subject = "parent-vs-guide-race-" + UUID.randomUUID();
        Jwt token = jwt(subject);

        Race<OnboardingResponse, OnboardingResponse> race =
                race(
                        () ->
                                onboardingService.onboardParticipant(
                                        token, participantRequest("PARENT")),
                        () -> onboardingService.onboardGuide(token, guideRequest(universityId)));

        List<RaceOutcome<OnboardingResponse>> outcomes = List.of(race.first(), race.second());
        long successCount = outcomes.stream().filter(RaceOutcome::succeeded).count();
        assertThat(successCount).isEqualTo(1);

        RaceOutcome<OnboardingResponse> loser =
                outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow();
        assertThat(loser.error()).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) loser.error()).code()).isEqualTo("ROLE_NOT_ELIGIBLE");

        // The I13 exclusion holds in the FINAL state regardless of which side won: never both.
        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        boolean hasGuide = userRoles.existsByUserIdAndRole(userId, UserRole.GUIDE);
        boolean hasParticipant = userRoles.existsByUserIdAndRole(userId, UserRole.PARTICIPANT);
        assertThat(hasGuide ^ hasParticipant).isTrue();
    }

    // ---- case 3: PROSPECTIVE participant vs GUIDE, same identity -> both may succeed ------

    @Test
    void onboardParticipantProspective_concurrentWithOnboardGuide_bothSucceed_noExclusion() {
        UUID universityId = seedUniversity();
        String subject = "prospective-and-guide-race-" + UUID.randomUUID();
        Jwt token = jwt(subject);

        Race<OnboardingResponse, OnboardingResponse> race =
                race(
                        () ->
                                onboardingService.onboardParticipant(
                                        token, participantRequest("PROSPECTIVE")),
                        () -> onboardingService.onboardGuide(token, guideRequest(universityId)));

        assertThat(race.first().succeeded())
                .as("participant(PROSPECTIVE) outcome: %s", race.first().error())
                .isTrue();
        assertThat(race.second().succeeded())
                .as("guide outcome: %s", race.second().error())
                .isTrue();

        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        assertThat(userRoles.findByUserId(userId))
                .extracting(UserRoleEntity::getRole)
                .containsExactlyInAnyOrder(UserRole.PARTICIPANT, UserRole.GUIDE);
        assertThat(guideProfiles.findByUserId(userId)).isPresent();
        assertThat(participantProfiles.findByUserId(userId)).isPresent();
    }

    // ---- case 4: PATCH participant type -> PARENT vs GUIDE onboarding, same identity ------

    @Test
    void participantPatchToParent_concurrentWithOnboardGuide_i13ExclusionUpheld() {
        UUID universityId = seedUniversity();
        String subject = "patch-parent-vs-guide-race-" + UUID.randomUUID();
        Jwt token = jwt(subject);
        OidcIdentity identity = new OidcIdentity(token.getIssuer().toString(), token.getSubject());

        // Seed an already-provisioned PROSPECTIVE participant BEFORE the race (Task 9's baseline:
        // the type-change lock only matters once a participant profile already exists).
        onboardingService.onboardParticipant(token, participantRequest("PROSPECTIVE"));
        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        assertThat(participantProfiles.findByUserId(userId).orElseThrow().getParticipantType())
                .isEqualTo(ParticipantType.PROSPECTIVE);

        Race<ParticipantProfileResponse, OnboardingResponse> race =
                race(
                        () ->
                                participantService.updateProfile(
                                        users.findByOidcSubject(subject).orElseThrow(),
                                        identity,
                                        patchToParentRequest()),
                        () -> onboardingService.onboardGuide(token, guideRequest(universityId)));

        boolean patchSucceeded = race.first().succeeded();
        boolean guideSucceeded = race.second().succeeded();
        // Exactly one of the two racing mutations may win -- I13 forbids both.
        assertThat(patchSucceeded ^ guideSucceeded).isTrue();

        if (guideSucceeded) {
            // The PATCH lost: ParticipantService.updateProfile's own inline I13 check now throws
            // the same ConflictException (409 ROLE_NOT_ELIGIBLE) as OnboardingService's pre-check
            // (CTL-97 Minor-3 aligned the two).
            assertThat(race.first().error()).isInstanceOf(ConflictException.class);
            assertThat(((ConflictException) race.first().error()).code())
                    .isEqualTo("ROLE_NOT_ELIGIBLE");
        } else {
            // The guide onboarding lost: OnboardingService's pre-check throws the documented 409.
            assertThat(race.second().error()).isInstanceOf(ConflictException.class);
            assertThat(((ConflictException) race.second().error()).code())
                    .isEqualTo("ROLE_NOT_ELIGIBLE");
        }

        // The I13 invariant itself, independent of which side lost: GUIDE and participantType ==
        // PARENT are never BOTH true for the same identity in the final committed state.
        boolean hasGuideRole = userRoles.existsByUserIdAndRole(userId, UserRole.GUIDE);
        ParticipantType finalType =
                participantProfiles.findByUserId(userId).orElseThrow().getParticipantType();
        assertThat(hasGuideRole && finalType == ParticipantType.PARENT).isFalse();
    }

    // ---- case 5: forced mid-transaction failure AFTER the audit write rolls back the audit row

    @Test
    void onboardGuide_forcedFailureAfterAuditWrite_rollsBackEverythingIncludingAuditRow() {
        UUID universityId = seedUniversity();
        String subject = "forced-rollback-" + UUID.randomUUID();

        // recordAudit() writes ACCOUNT_PROVISIONED first (real -- callRealMethod), then
        // ROLE_ACQUIRED second; stubbing only the second call forces the throw strictly AFTER a
        // real in-transaction audit insert has already happened, but still inside the same
        // @Transactional onboardGuide call -- so its rollback must take that first row with it too.
        doAnswer(
                        invocation -> {
                            String action = invocation.getArgument(0);
                            if ("ROLE_ACQUIRED".equals(action)) {
                                throw new RuntimeException(
                                        "forced mid-transaction failure after audit write");
                            }
                            return invocation.callRealMethod();
                        })
                .when(auditWriter)
                .record(anyString(), anyString(), anyString(), any(UUID.class), anyMap());

        assertThatThrownBy(
                        () ->
                                onboardingService.onboardGuide(
                                        jwt(subject), guideRequest(universityId)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced mid-transaction failure");

        // The whole transaction rolled back: no user row survives...
        assertThat(users.findByOidcSubject(subject)).isEmpty();

        // ...and critically, NOT EVEN the ACCOUNT_PROVISIONED audit row that was written for REAL
        // (via the spy's callRealMethod) before the forced throw survives. Capture the actor id the
        // real call used (the row itself is gone -- there is no other way to find it) and assert
        // zero audit_log rows exist for it: audit is in-transaction, never fire-and-forget.
        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(auditWriter)
                .record(
                        eq("ACCOUNT_PROVISIONED"),
                        eq("user"),
                        anyString(),
                        actorCaptor.capture(),
                        anyMap());
        UUID attemptedUserId = actorCaptor.getValue();
        assertThat(auditLogs.findByTargetTypeAndTargetId("user", attemptedUserId.toString()))
                .isEmpty();
    }
}
