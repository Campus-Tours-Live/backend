package com.CampusToursLive.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.security.AccountResolution;
import com.CampusToursLive.security.AccountResolver;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BLOCKING concurrency proof (real PostgreSQL, Testcontainers) for {@link AccountResolver}'s
 * single-snapshot property.
 *
 * <p>Setup mirrors {@link AccountProjectionQueryTest}. A GUIDE account is seeded and committed.
 * Then, on a separate writer thread inside ONE latch-gated transaction, a SECOND role (PARTICIPANT)
 * and its {@code participant_profile} row are inserted and committed. A reader (the test's main
 * thread, using its own separate connections) calls {@link
 * AccountResolver#resolveAuthenticatedIdentity(Jwt)} both BEFORE the writer's commit (while the
 * writer's transaction is open and uncommitted, holding one row inserted but not the other) and
 * AFTER it. Because the resolver reads everything via {@code
 * UserRepository#findAccountProjectionByOidcSubject} — ONE native query, ONE MVCC snapshot — the
 * BEFORE call can only ever see the complete OLD state (roles={GUIDE}) and the AFTER call only the
 * complete NEW state (roles={GUIDE,PARTICIPANT}); it can never observe the writer's transaction
 * half-applied (e.g. role granted but its profile not yet present, or vice versa).
 *
 * <p>Both writer insert orderings are exercised — {@code participant_profile} then {@code
 * user_role} ({@link #resolverNeverObservesPartialState_whenProfileCommittedBeforeRole()}), and the
 * reverse ({@link #resolverNeverObservesPartialState_whenRoleCommittedBeforeProfile()}) — proving
 * the guarantee doesn't depend on which row the writer happens to insert first.
 *
 * <p>Each scenario also drives an inline, deliberately naive {@link TwoQueryControlResolver} — NOT
 * shipped, exists only in this test — that reads roles and the participant profile as TWO separate
 * queries/snapshots. Its first read is taken in the same BEFORE window (stale: no PARTICIPANT role
 * yet) and its second read in the same AFTER window (fresh: the participant profile now exists).
 * Combined, those two straddled reads reproduce exactly the false-positive {@code
 * ROLE_PROFILE_STATE_INVALID(PARTICIPANT)} verdict — a role-less orphan profile — that a real
 * multi-query resolver could emit under this interleaving. This is the concrete "why" behind the
 * single-query requirement: {@link AccountResolver} itself, asked at the very same BEFORE/AFTER
 * instants, never returns anything but a fully-consistent {@link AccountResolution.Provisioned}.
 *
 * <p>Class-level {@code @Transactional(NOT_SUPPORTED)} overrides {@code @DataJpaTest}'s default
 * per-test transactional rollback wrapper: this test needs REAL, independently committing
 * transactions on separate connections (reader vs. writer thread), not one shared, rolled-back test
 * transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountResolverSnapshotTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final long LATCH_TIMEOUT_SECONDS = 10;

    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private ParticipantProfileRepository participantProfiles;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private static UserEntity user(String subject) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject(subject);
        u.setEmail(subject + "@example.com");
        u.setFirstName("Ada");
        u.setLastName("Lovelace");
        u.setDisplayName("Ada Lovelace");
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setAgeBand(AgeBand.ADULT);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }

    private static GuideProfileEntity guideProfileFor(UUID userId) {
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(userId);
        guide.setStatus(GuideStatus.VERIFIED);
        return guide;
    }

    private static ParticipantProfileEntity participantProfileFor(UUID userId) {
        ParticipantProfileEntity participant = new ParticipantProfileEntity();
        participant.setId(UUID.randomUUID());
        participant.setUserId(userId);
        participant.setParticipantType(ParticipantType.TRANSFER);
        return participant;
    }

    private static void awaitLatch(CountDownLatch latch, String description) {
        try {
            boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new AssertionError("Timed out waiting for: " + description);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for: " + description, e);
        }
    }

    /**
     * A deliberately naive multi-query resolver — read roles, then read the profile, as TWO
     * separate queries/snapshots — kept ONLY in this test to prove why {@link AccountResolver}'s
     * single query is the blocking requirement. Never shipped.
     */
    private final class TwoQueryControlResolver {

        boolean readParticipantRole(UUID userId) {
            return userRoles.existsByUserIdAndRole(userId, UserRole.PARTICIPANT);
        }

        boolean readParticipantProfilePresent(UUID userId) {
            return participantProfiles.findByUserId(userId).isPresent();
        }

        /** Mirrors {@code AccountResolver#checkRoleProfilePairing}'s PARTICIPANT branch exactly. */
        boolean wouldFlagRoleProfileStateInvalid(
                boolean hasParticipantRole, boolean profilePresent) {
            if (hasParticipantRole) {
                return !profilePresent; // held without exactly one profile
            }
            return profilePresent; // orphan profile: present for a role not held
        }
    }

    @Test
    void resolverNeverObservesPartialState_whenProfileCommittedBeforeRole() throws Exception {
        runOrderedConcurrencyScenario("acct-race-profile-first", /* profileInsertedFirst= */ true);
    }

    @Test
    void resolverNeverObservesPartialState_whenRoleCommittedBeforeProfile() throws Exception {
        runOrderedConcurrencyScenario("acct-race-role-first", /* profileInsertedFirst= */ false);
    }

    private void runOrderedConcurrencyScenario(String oidcSubject, boolean profileInsertedFirst)
            throws Exception {
        UserEntity saved = users.saveAndFlush(user(oidcSubject));
        UUID userId = saved.getId();
        userRoles.saveAndFlush(new UserRoleEntity(userId, UserRole.GUIDE));
        guideProfiles.saveAndFlush(guideProfileFor(userId));

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(oidcSubject).build();
        AccountResolver resolver = new AccountResolver(users);
        TwoQueryControlResolver control = new TwoQueryControlResolver();

        CountDownLatch writerInsertedFirstRow = new CountDownLatch(1);
        CountDownLatch readerFinishedBeforeChecks = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        TransactionTemplate writerTx = new TransactionTemplate(transactionManager);
        writerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<Void> writerTask =
                    () -> {
                        writerTx.executeWithoutResult(
                                status -> {
                                    if (profileInsertedFirst) {
                                        participantProfiles.saveAndFlush(
                                                participantProfileFor(userId));
                                    } else {
                                        userRoles.saveAndFlush(
                                                new UserRoleEntity(userId, UserRole.PARTICIPANT));
                                    }
                                    writerInsertedFirstRow.countDown();
                                    awaitLatch(
                                            readerFinishedBeforeChecks,
                                            "reader to finish its before-commit checks");
                                    if (profileInsertedFirst) {
                                        userRoles.saveAndFlush(
                                                new UserRoleEntity(userId, UserRole.PARTICIPANT));
                                    } else {
                                        participantProfiles.saveAndFlush(
                                                participantProfileFor(userId));
                                    }
                                });
                        writerCommitted.countDown();
                        return null;
                    };
            Future<Void> writerFuture = executor.submit(writerTask);

            awaitLatch(writerInsertedFirstRow, "writer to insert its first (uncommitted) row");

            Statistics stats =
                    entityManager
                            .getEntityManagerFactory()
                            .unwrap(SessionFactory.class)
                            .getStatistics();
            stats.setStatisticsEnabled(true);

            // BEFORE the writer commits: only one of {participant_profile, user_role} exists, and
            // it is UNCOMMITTED — invisible to any other transaction under READ COMMITTED. The
            // real resolver's single query must therefore see the complete OLD snapshot only.
            stats.clear();
            AccountResolution before = resolver.resolveAuthenticatedIdentity(jwt);
            assertThat(stats.getPrepareStatementCount())
                    .as("resolver must issue exactly one statement")
                    .isEqualTo(1);
            assertThat(before).isNotInstanceOf(AccountResolution.Invalid.class);
            assertThat(before).isInstanceOf(AccountResolution.Provisioned.class);
            assertThat(((AccountResolution.Provisioned) before).account().roles())
                    .as("before commit: only the original GUIDE role is visible")
                    .containsExactly(UserRole.GUIDE);

            // Control's FIRST query, sampled in this same BEFORE window: no PARTICIPANT role yet.
            boolean controlRoleBefore = control.readParticipantRole(userId);
            assertThat(controlRoleBefore).isFalse();

            readerFinishedBeforeChecks.countDown();
            awaitLatch(writerCommitted, "writer to commit");
            writerFuture.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // AFTER the writer commits: both rows are visible together (atomic commit) — the real
            // resolver's single query must see the complete NEW snapshot.
            stats.clear();
            AccountResolution after = resolver.resolveAuthenticatedIdentity(jwt);
            assertThat(stats.getPrepareStatementCount())
                    .as("resolver must issue exactly one statement")
                    .isEqualTo(1);
            assertThat(after).isNotInstanceOf(AccountResolution.Invalid.class);
            assertThat(after).isInstanceOf(AccountResolution.Provisioned.class);
            assertThat(((AccountResolution.Provisioned) after).account().roles())
                    .as("after commit: both GUIDE and PARTICIPANT are visible together")
                    .containsExactlyInAnyOrder(UserRole.GUIDE, UserRole.PARTICIPANT);

            // Control's SECOND query, sampled in this same AFTER window: the profile now exists.
            boolean controlProfileAfter = control.readParticipantProfilePresent(userId);
            assertThat(controlProfileAfter).isTrue();

            // Combine the control's two straddled reads exactly as a naive two-query resolver
            // would: it would see a PARTICIPANT profile with no matching PARTICIPANT role — a
            // false ROLE_PROFILE_STATE_INVALID(PARTICIPANT) — precisely the hazard the real,
            // single-query AccountResolver is immune to (proven above: `before`/`after` were
            // always a fully consistent Provisioned account, never Invalid).
            assertThat(
                            control.wouldFlagRoleProfileStateInvalid(
                                    controlRoleBefore, controlProfileAfter))
                    .as(
                            "a multi-query resolver reading roles-then-profile across the writer's "
                                    + "commit boundary CAN observe a false ROLE_PROFILE_STATE_INVALID"
                                    + "(PARTICIPANT) — this is why AccountResolver must stay single-query")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
