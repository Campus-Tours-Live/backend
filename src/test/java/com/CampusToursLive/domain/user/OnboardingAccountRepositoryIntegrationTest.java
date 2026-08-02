package com.CampusToursLive.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers proof that {@link OnboardingAccountRepository#findAnyByOidcSubject(String)} is
 * lifecycle-inclusive — CTL-97 Core-B Task 2.
 *
 * <p>{@link UserEntity} carries no {@code @Where} soft-delete filter, so a plain lookup by {@code
 * oidc_subject} already returns every lifecycle state; this test pins that behavior against a real
 * database for the two states the onboarding write path most needs to see: a soft-deleted row
 * ({@code deletedAt} set AND {@code accountStatus == DELETED}) and a SUSPENDED row. Contrast with
 * {@code AccountResolverSnapshotTest} / {@code AccountProjectionQueryTest}, which prove the
 * READ-side {@link com.CampusToursLive.security.AccountResolver} path — that resolver treats
 * SUSPENDED/DELETED as terminal verdicts rather than rows to act on. This class proves the
 * WRITE-side counterpart never silently drops those rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OnboardingAccountRepositoryIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private OnboardingAccountRepository onboardingAccounts;
    @Autowired private UserRepository users;

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

    @Test
    void findAnyByOidcSubject_returnsSoftDeletedRow() {
        String subject = "sub-deleted-" + UUID.randomUUID();
        UserEntity deleted = user(subject);
        deleted.setAccountStatus(AccountStatus.DELETED);
        deleted.setDeletedAt(Instant.now());
        users.saveAndFlush(deleted);

        Optional<UserEntity> found = onboardingAccounts.findAnyByOidcSubject(subject);

        assertThat(found).isPresent();
        assertThat(found.get().getAccountStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(found.get().getDeletedAt()).isNotNull();
    }

    @Test
    void findAnyByOidcSubject_returnsSuspendedRow() {
        String subject = "sub-suspended-" + UUID.randomUUID();
        UserEntity suspended = user(subject);
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(suspended);

        Optional<UserEntity> found = onboardingAccounts.findAnyByOidcSubject(subject);

        assertThat(found).isPresent();
        assertThat(found.get().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(found.get().getDeletedAt()).isNull();
    }

    @Test
    void findAnyByOidcSubject_returnsEmpty_whenNoRowMatchesTheSubject() {
        String subject = "no-such-subject-" + UUID.randomUUID();

        assertThat(onboardingAccounts.findAnyByOidcSubject(subject)).isEmpty();
    }
}
