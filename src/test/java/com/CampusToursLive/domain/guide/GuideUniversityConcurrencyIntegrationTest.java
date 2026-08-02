package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers (real PostgreSQL, full Spring context) proof that the {@code @Version} column on
 * {@code guide_universities} closes the write-skew window CTL-97 opened by making {@code entryYear}
 * and {@code classYear} interdependent (spec I3/D8).
 *
 * <p><b>The hole per-request validation cannot close.</b> From a stored {@code (entryYear 2020,
 * classYear 2024)}: a PATCH setting {@code entryYear=2023} validates {@code (2023, stored 2024)} —
 * legal; a concurrent PATCH setting {@code classYear=2021} validates {@code (stored 2020, 2021)} —
 * also legal. Neither write is wrong on its own, so no amount of validation on either path can see
 * the problem; only their interleaving can. A version check can, and does: the loser fails with
 * {@link OptimisticLockingFailureException}.
 *
 * <p><b>This test proves the exception, NOT the 409.</b> It exercises the service and repository,
 * so the strongest thing it can assert is the exception type. The mapping to HTTP 409 lives in
 * {@code GlobalExceptionHandler} and is covered by {@code GlobalExceptionHandlerTest} — do not read
 * a green run here as evidence that mapping still exists.
 *
 * <p><b>Naming.</b> This repo has no failsafe plugin, so a {@code *IT.java} class is silently
 * skipped by {@code ./mvnw verify}'s surefire-only run. Named {@code *IntegrationTest} so surefire
 * executes it, matching {@code OnboardingConcurrencyIntegrationTest}.
 *
 * <p><b>Wiring</b> copied from {@code AvailabilityRematerializeLockConcurrencyIntegrationTest}:
 * full {@code @SpringBootTest} (not {@code @DataJpaTest}) because a real two-transaction race needs
 * the production {@code @Transactional} proxy on {@link GuideService#updateProfile} to open a
 * genuinely separate transaction — and therefore a separate DB connection — per caller. Under
 * {@code @DataJpaTest} the whole test would run in ONE rolled-back transaction and the version
 * check could never be observed to fire. The context boots offline via a mocked {@link JwtDecoder}.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideUniversityConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private GuideService guideService;
    @Autowired private GuideUniversityRepository guideUniversities;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private UniversityRepository universities;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager txManager;

    private static final String MAJOR = "Marine Biology";
    private static final long BARRIER_TIMEOUT_SECONDS = 20;

    private UUID userId;
    private UUID universityId;
    private UUID guideUniversityId;
    private String degree;

    /**
     * Write skew: each request is valid against the snapshot it read, the pair is not. Exactly one
     * must survive.
     */
    @Test
    void concurrentEditsCannotCommitAnInvalidPair() throws Exception {
        // Arrange: a stored, legal pair. Bachelor's → classYear window [2021, 2026].
        seedGuideUniversity(/* entryYear */ 2020, /* classYear */ "2024", "Bachelor's Degree");

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2, namedThreadFactory("year-skew"));
        pool.submit(
                () -> {
                    try {
                        // A: entryYear → 2023, checked against the stored classYear 2024. Legal.
                        runInTransactionAfterBothHaveRead(
                                bothRead, release, () -> patchEntryYear(2023));
                    } catch (Throwable t) {
                        failureA.set(t);
                    }
                });
        pool.submit(
                () -> {
                    try {
                        // B: classYear → 2021, checked against the stored entryYear 2020. Legal.
                        runInTransactionAfterBothHaveRead(
                                bothRead, release, () -> patchClassYear("2021"));
                    } catch (Throwable t) {
                        failureB.set(t);
                    }
                });

        // TIMEOUTS, not bare awaits. If a worker dies before reaching the barrier, an untimed
        // await() hangs this test forever — and a hung build is diagnosed far more slowly than a
        // failed one. The finally block releases and interrupts so a failure here cannot leave
        // threads running into the next test.
        //
        // shutdown() BEFORE awaitTermination() is mandatory, not tidiness: awaitTermination only
        // returns true "after a shutdown request". Without it the call blocks for the full timeout
        // and returns false, so this assertion fails every run — 30 wasted seconds before a
        // failure that says "deadlock" about a test that never deadlocked.
        try {
            assertTrue(
                    bothRead.await(10, TimeUnit.SECONDS),
                    () ->
                            "both workers must reach the read barrier; one died early. A="
                                    + failureA.get()
                                    + " B="
                                    + failureB.get());
            release.countDown();
            pool.shutdown();
            assertTrue(
                    pool.awaitTermination(30, TimeUnit.SECONDS),
                    "workers did not finish — likely a deadlock, not a lock conflict");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        boolean aFailed = failureA.get() != null;
        boolean bFailed = failureB.get() != null;
        assertTrue(aFailed ^ bFailed, "exactly one writer must lose");

        // ...and it must lose for the RIGHT REASON. Asserting only "something threw" would let a
        // NullPointerException, a broken fixture, a SQL syntax error or a transaction timeout
        // stand in for the optimistic-lock conflict this task exists to produce — the test would
        // be green while the guarantee was absent.
        Throwable loser = aFailed ? failureA.get() : failureB.get();
        assertTrue(
                hasCause(loser, OptimisticLockingFailureException.class),
                "loser must fail on optimistic locking, but was: " + loser);

        // The two assertions above are the load-bearing ones: the exclusive-or (exactly one writer
        // must lose) and the cause-chain check (it lost with OptimisticLockingFailureException
        // specifically). What follows is NOT "the assertion the whole task exists for" — without
        // @DynamicUpdate the winner writes every column from its own consistent snapshot, so the
        // surviving pair is legal by construction and a legality check alone could not fail.
        //
        // What it CAN catch is the winner's edit not landing at all: a bug that rolled both
        // transactions back while still bumping the version would satisfy everything above. So
        // assert the exact surviving row is one of the two intended outcomes — A's (entryYear 2023
        // over the stored classYear 2024) or B's (classYear 2021 over the stored entryYear 2020).
        // Naming the outcomes also keeps the bachelor's-window arithmetic out of this file; a
        // hardcoded `entry + 6` here would be a second copy of a rule that lives in
        // EnrollmentYearRules, which is exactly the drift invariant I1 forbids.
        GuideUniversityEntity row = reloadGuideUniversity();
        String survivor = row.getEntryYear() + "/" + row.getClassYear();
        assertTrue(
                survivor.equals("2023/2024") || survivor.equals("2020/2021"),
                "the winner's edit must have landed: expected 2023/2024 (A won) or 2020/2021"
                        + " (B won), but the row holds "
                        + survivor);
        assertEquals(1L, row.getVersion());
    }

    /** Walks the cause chain — the exception arrives wrapped by the transaction infrastructure. */
    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) return true;
        }
        return false;
    }

    // ---- fixtures --------------------------------------------------------------------------

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }

    /**
     * Seeds the whole chain this race needs — user → guide profile → university →
     * guide_universities — through the repositories, so the row is created by exactly the JPA path
     * the service later updates (and so {@code version} starts at the entity default, 0).
     *
     * <p>{@code entry_year} is NOT NULL at the database layer since Task 5, so this fixture MUST
     * supply one; that is why the seed takes it as a parameter rather than defaulting it.
     */
    private void seedGuideUniversity(int entryYear, String classYear, String degreeValue) {
        this.degree = degreeValue;

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setOidcSubject("year-skew-" + UUID.randomUUID());
        user.setEmail("year-skew-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Year Skew Guide");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Los_Angeles");
        users.saveAndFlush(user);
        this.userId = user.getId();

        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setUserId(user.getId());
        profile.setStatus(GuideStatus.VERIFIED);
        guideProfiles.saveAndFlush(profile);

        UniversityEntity university = new UniversityEntity();
        university.setId(UUID.randomUUID());
        university.setSlug("year-skew-uni-" + UUID.randomUUID());
        university.setName("Year Skew Test University " + UUID.randomUUID());
        university.setCity("Testville");
        university.setTimezone("America/Los_Angeles");
        university.setStatus(UniversityStatus.ACTIVE);
        universities.saveAndFlush(university);
        this.universityId = university.getId();

        GuideUniversityEntity row = new GuideUniversityEntity();
        row.setId(UUID.randomUUID());
        row.setGuideProfileId(profile.getId());
        row.setUniversityId(university.getId());
        row.setMajor(MAJOR);
        row.setDegree(degreeValue);
        row.setClassYear(classYear);
        row.setEntryYear(entryYear);
        guideUniversities.saveAndFlush(row);
        this.guideUniversityId = row.getId();
    }

    /**
     * A single-field PATCH through the REAL service path (the merged-pair validation from Task 4
     * included). universityId/major/degree are mandatory on every PATCH and carry their stored
     * values, so the ONLY field this request changes is the year named by the caller.
     */
    private GuideProfileUpdateRequest patchRequest(Integer entryYear, String classYear) {
        return new GuideProfileUpdateRequest(
                null,
                null,
                universityId.toString(),
                MAJOR,
                classYear,
                null,
                null,
                null,
                null,
                null,
                degree,
                entryYear);
    }

    private void patchEntryYear(int entryYear) {
        guideService.updateProfile(
                users.findById(userId).orElseThrow(), patchRequest(entryYear, null));
    }

    private void patchClassYear(String classYear) {
        guideService.updateProfile(
                users.findById(userId).orElseThrow(), patchRequest(null, classYear));
    }

    /**
     * Opens a REAL transaction, takes this worker's SNAPSHOT of the row, and holds the transaction
     * open at the barrier until BOTH workers have read — only then does it run the patch. Without
     * the barrier one transaction finishes before the other starts and there is no conflict to
     * detect. The latch is the test, not scaffolding around it.
     *
     * <p><b>Why the read is here and not left to the patch.</b> {@link GuideService#updateProfile}
     * ends by building its response from a {@code guide_universities} query, and a JPA query with
     * dirty state pending on that table AUTO-FLUSHES first. So the versioned UPDATE — and the row
     * lock it takes — happens INSIDE the service call, before it returns. A harness that counted
     * down only after {@code patch.run()} would let the first worker lock the row while the second
     * blocked inside its own service call, so the second never reaches the barrier and the test
     * times out rather than racing. Reading first puts BOTH workers' version snapshots (0) into
     * their persistence contexts before either writes, which is the race the spec describes: each
     * worker validates its merged pair against the state it read.
     *
     * <p>Because the same query returns the ALREADY-managed instance rather than overwriting it
     * with fresher column values, the loser still holds the pre-race snapshot when it flushes — its
     * {@code UPDATE ... WHERE id = ? AND version = 0} matches zero rows once the winner commits.
     *
     * <p>The trailing explicit {@code flush()} is the safety net for the same reason the read
     * moved: optimistic locking fires at flush/commit and never at {@code setX()} or {@code
     * save()}. It guarantees the versioned UPDATE is inside the caller's try/catch even if the
     * service's auto-flush ever goes away — and the caller catches around this whole method, commit
     * included.
     */
    private void runInTransactionAfterBothHaveRead(
            CountDownLatch bothRead, CountDownLatch release, Runnable patch) {
        new TransactionTemplate(txManager)
                .executeWithoutResult(
                        status -> {
                            reloadGuideUniversity(); // READ: version 0, (2020, 2024).
                            bothRead.countDown();
                            awaitOrFail(release, "release latch never fired");
                            patch.run(); // validates against the snapshot above, then writes.
                            guideUniversities.flush(); // versioned UPDATE, if not already flushed.
                        });
    }

    private static void awaitOrFail(CountDownLatch latch, String message) {
        try {
            if (!latch.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }

    private GuideUniversityEntity reloadGuideUniversity() {
        return guideUniversities.findById(guideUniversityId).orElseThrow();
    }
}
