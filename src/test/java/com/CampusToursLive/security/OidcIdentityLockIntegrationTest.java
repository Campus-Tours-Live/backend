package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
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
 * Deterministic concurrency proof (real PostgreSQL, Testcontainers) for {@link
 * PostgresOidcIdentityLock} — CTL-97 Core-B Task 1.
 *
 * <p>Mirrors {@code AvailabilityRematerializeLockConcurrencyIntegrationTest} / {@code
 * AccountResolverSnapshotTest}'s style: real, independently-committing transactions on separate
 * connections (driven via {@link TransactionTemplate} with {@code REQUIRES_NEW} on separate
 * threads), coordinated with {@link CountDownLatch}es — never {@code Thread.sleep} — so the
 * blocking/non-blocking assertions can never flake.
 *
 * <p>Class-level {@code @Transactional(NOT_SUPPORTED)} overrides {@code @DataJpaTest}'s default
 * per-test rollback wrapper, since each scenario needs its OWN independently
 * committing/rolling-back transactions rather than one shared, always-rolled-back test transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(PostgresOidcIdentityLock.class)
class OidcIdentityLockIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final long LATCH_TIMEOUT_SECONDS = 10;

    @Autowired private OidcIdentityLock lock;
    @Autowired private PlatformTransactionManager transactionManager;

    private static void awaitLatch(CountDownLatch latch, String description) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for: " + description);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for: " + description, e);
        }
    }

    private TransactionTemplate newRequiresNewTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    @Test
    void acquire_serializesConcurrentCallers_forSameIdentity_andReleasesOnCommit()
            throws Exception {
        OidcIdentity identity =
                new OidcIdentity("https://accounts.google.com", "sub-" + UUID.randomUUID());
        assertSerializesAndReleases(identity, /* commit= */ true);
    }

    @Test
    void acquire_releasesOnRollback_soASecondAcquisitionSucceedsAfterwards() throws Exception {
        OidcIdentity identity =
                new OidcIdentity("https://accounts.google.com", "sub-" + UUID.randomUUID());
        assertSerializesAndReleases(identity, /* commit= */ false);
    }

    /**
     * Thread A acquires {@code identity}'s lock inside its own {@code REQUIRES_NEW} transaction and
     * holds it behind a latch. While held, a concurrent Thread B acquiring the SAME identity must
     * BLOCK (a 1s future-get must time out) — that alone proves serialization, since nothing but
     * the advisory lock could stop B. Releasing A (committing or rolling back, per {@code commit})
     * must then let B's acquisition complete within the full timeout — proving auto-release on that
     * outcome. (Deliberately no wall-clock {@code Instant} comparison across the two threads: the
     * timeout-then-succeeds pattern already fully orders "B blocked while held" before "B completed
     * once released" without racing on clock reads taken in two independent threads.)
     */
    private void assertSerializesAndReleases(OidcIdentity identity, boolean commit)
            throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();

        Thread holder =
                new Thread(
                        () -> {
                            try {
                                newRequiresNewTx()
                                        .executeWithoutResult(
                                                status -> {
                                                    lock.acquire(identity);
                                                    lockHeld.countDown();
                                                    awaitLatch(
                                                            releaseHolder,
                                                            "release signal for lock holder");
                                                    if (!commit) {
                                                        status.setRollbackOnly();
                                                    }
                                                });
                            } catch (Throwable t) {
                                holderError.set(t);
                                lockHeld.countDown();
                            }
                        },
                        "lock-holder");
        holder.start();

        assertThat(lockHeld.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();

        ExecutorService execFollower = Executors.newSingleThreadExecutor();
        try {
            Future<?> follower =
                    execFollower.submit(
                            () ->
                                    newRequiresNewTx()
                                            .executeWithoutResult(
                                                    status -> lock.acquire(identity)));

            // Red signal: while the holder still has the lock, the follower must BLOCK at
            // acquisition and make no progress.
            assertThatThrownBy(() -> follower.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            assertThatCode(() -> follower.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
        } finally {
            releaseHolder.countDown();
            execFollower.shutdownNow();
            holder.join(5_000);
        }
    }

    @Test
    void acquire_doesNotBlock_forDifferentIdentities_evenWithTheSameSubject() throws Exception {
        String sharedSubject = "sub-" + UUID.randomUUID();
        OidcIdentity identityA = new OidcIdentity("https://accounts.google.com", sharedSubject);
        OidcIdentity identityB = new OidcIdentity("https://appleid.apple.com", sharedSubject);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();

        Thread holder =
                new Thread(
                        () -> {
                            try {
                                newRequiresNewTx()
                                        .executeWithoutResult(
                                                status -> {
                                                    lock.acquire(identityA);
                                                    lockHeld.countDown();
                                                    awaitLatch(
                                                            releaseHolder,
                                                            "release signal for lock holder");
                                                });
                            } catch (Throwable t) {
                                holderError.set(t);
                                lockHeld.countDown();
                            }
                        },
                        "lock-holder-A");
        holder.start();

        assertThat(lockHeld.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(holderError.get()).isNull();

        try {
            // While A still holds identityA's lock, B acquiring the DIFFERENT identity (same raw
            // subject, different issuer) must complete immediately — never blocked by A.
            assertThatCode(
                            () ->
                                    newRequiresNewTx()
                                            .executeWithoutResult(
                                                    status -> lock.acquire(identityB)))
                    .doesNotThrowAnyException();
        } finally {
            releaseHolder.countDown();
            holder.join(5_000);
        }
    }

    @Test
    void acquire_throwsIllegalStateException_whenNoActiveTransaction() {
        OidcIdentity identity = new OidcIdentity("https://accounts.google.com", "no-tx-subject");

        assertThatThrownBy(() -> lock.acquire(identity)).isInstanceOf(IllegalStateException.class);
    }
}
