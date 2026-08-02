package com.CampusToursLive.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Testcontainers proof (real PostgreSQL) for {@link AuditWriter} — CTL-97 Core-B Task 3.
 *
 * <p>Two things are pinned against a real database rather than an in-memory/mocked one: (1) a
 * {@code metadata} {@code jsonb} map round-trips byte-for-byte through Postgres and Hibernate's
 * {@code SqlTypes.JSON} mapping, and (2) {@link AuditWriter#record} carries no transaction boundary
 * of its own — it truly rides the CALLER's transaction, so a caller rollback takes the audit row
 * with it and a caller commit keeps it. Mirrors {@code OidcIdentityLockIntegrationTest}'s style:
 * real, independently-committing/rolling-back transactions driven via {@link TransactionTemplate},
 * with the class-level {@code @Transactional(NOT_SUPPORTED)} overriding {@code @DataJpaTest}'s
 * default always-rollback test wrapper so each scenario gets its OWN real commit or rollback to
 * observe.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(AuditWriter.class)
class AuditWriterIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private AuditWriter auditWriter;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate newRequiresNewTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private UUID seedUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setOidcSubject("audit-writer-" + UUID.randomUUID());
        user.setEmail("audit-writer-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Audit Writer Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Los_Angeles");
        newRequiresNewTx().executeWithoutResult(status -> users.save(user));
        return user.getId();
    }

    @Test
    void record_commits_insertsRowWithGeneratedIdAndRoundTrippedMetadata() {
        UUID actorUserId = seedUser();
        String targetId = "profile-" + UUID.randomUUID();
        Map<String, Object> metadata =
                Map.of("role", "GUIDE", "profileId", targetId, "accountCreated", true);

        newRequiresNewTx()
                .executeWithoutResult(
                        status ->
                                auditWriter.record(
                                        "ONBOARDING_COMPLETE",
                                        "guide_profile",
                                        targetId,
                                        actorUserId,
                                        metadata));

        List<AuditLogEntity> found =
                auditLogs.findByTargetTypeAndTargetId("guide_profile", targetId);
        assertThat(found).hasSize(1);

        AuditLogEntity row = found.get(0);
        assertThat(row.getId()).isNotNull();
        assertThat(row.getAction()).isEqualTo("ONBOARDING_COMPLETE");
        assertThat(row.getTargetType()).isEqualTo("guide_profile");
        assertThat(row.getTargetId()).isEqualTo(targetId);
        assertThat(row.getActorUserId()).isEqualTo(actorUserId);
        assertThat(row.getOccurredAt()).isNotNull();
        assertThat(row.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void record_callerRollsBack_auditRowIsAbsent() {
        UUID actorUserId = seedUser();
        String targetId = "profile-" + UUID.randomUUID();
        Map<String, Object> metadata = Map.of("role", "PARTICIPANT", "profileId", targetId);

        newRequiresNewTx()
                .executeWithoutResult(
                        status -> {
                            auditWriter.record(
                                    "ONBOARDING_COMPLETE",
                                    "participant_profile",
                                    targetId,
                                    actorUserId,
                                    metadata);
                            status.setRollbackOnly();
                        });

        assertThat(auditLogs.findByTargetTypeAndTargetId("participant_profile", targetId))
                .isEmpty();
    }
}
