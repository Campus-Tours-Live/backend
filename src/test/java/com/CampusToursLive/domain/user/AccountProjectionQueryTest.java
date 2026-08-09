package com.CampusToursLive.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@code UserRepository#findAccountProjectionByOidcSubject} against a REAL
 * PostgreSQL (Testcontainers) — the only way to prove the single native {@code LEFT JOIN}/{@code
 * GROUP BY} query actually maps onto {@link AccountProjection} (role flags, profile counts, {@code
 * participant_type}) against the real V1 schema, that resolving an identity issues exactly ONE
 * prepared statement, and that the unique constraints {@link AccountResolver}'s exactly-one-profile
 * invariant depends on ({@code guide_profiles.user_id}, {@code participant_profiles.user_id})
 * actually exist at the DB level.
 *
 * <p>Deliberately does NOT try to fabricate a {@code count = 2} scenario here — the schema's own
 * unique constraints forbid a second profile row per user, so that branch (a corrupt/duplicated
 * profile row) is covered only by the mocked {@code AccountResolverTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountProjectionQueryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private ParticipantProfileRepository participantProfiles;
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

    private static ParticipantProfileEntity participantProfileFor(
            UUID userId, ParticipantType type) {
        ParticipantProfileEntity participant = new ParticipantProfileEntity();
        participant.setId(UUID.randomUUID());
        participant.setUserId(userId);
        participant.setParticipantType(type);
        return participant;
    }

    @Test
    void returnsEmpty_whenNoSuchUser() {
        assertThat(users.findAccountProjectionByOidcSubject("no-such-subject")).isEmpty();
    }

    @Test
    void mapsAdminOnlyAccount_withNoProfiles() {
        UserEntity saved = users.saveAndFlush(user("acct-admin"));
        userRoles.saveAndFlush(new UserRoleEntity(saved.getId(), UserRole.ADMIN));

        AccountProjection p = users.findAccountProjectionByOidcSubject("acct-admin").orElseThrow();

        assertThat(p.getId()).isEqualTo(saved.getId());
        assertThat(p.getOidcSubject()).isEqualTo("acct-admin");
        assertThat(p.getEmail()).isEqualTo("acct-admin@example.com");
        assertThat(p.getFirstName()).isEqualTo("Ada");
        assertThat(p.getLastName()).isEqualTo("Lovelace");
        assertThat(p.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(p.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(p.getAgeBand()).isEqualTo("ADULT");
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getDeletedAt()).isNull();
        assertThat(p.getAdminRole()).isTrue();
        assertThat(p.getGuideRole()).isFalse();
        assertThat(p.getParticipantRole()).isFalse();
        assertThat(p.getSupportRole()).isFalse();
        assertThat(p.getGuideProfileCount()).isZero();
        assertThat(p.getParticipantProfileCount()).isZero();
        assertThat(p.getParticipantTypeCount()).isZero();
        assertThat(p.getParticipantType()).isNull();
    }

    @Test
    void countsGuideProfile_whenGuideRoleHeld() {
        UserEntity saved = users.saveAndFlush(user("acct-guide"));
        userRoles.saveAndFlush(new UserRoleEntity(saved.getId(), UserRole.GUIDE));
        guideProfiles.saveAndFlush(guideProfileFor(saved.getId()));

        AccountProjection p = users.findAccountProjectionByOidcSubject("acct-guide").orElseThrow();

        assertThat(p.getGuideRole()).isTrue();
        assertThat(p.getGuideProfileCount()).isEqualTo(1);
        assertThat(p.getParticipantProfileCount()).isZero();
    }

    @Test
    void countsParticipantProfileAndType_whenParticipantRoleHeld() {
        UserEntity saved = users.saveAndFlush(user("acct-participant"));
        userRoles.saveAndFlush(new UserRoleEntity(saved.getId(), UserRole.PARTICIPANT));
        participantProfiles.saveAndFlush(
                participantProfileFor(saved.getId(), ParticipantType.TRANSFER));

        AccountProjection p =
                users.findAccountProjectionByOidcSubject("acct-participant").orElseThrow();

        assertThat(p.getParticipantRole()).isTrue();
        assertThat(p.getParticipantProfileCount()).isEqualTo(1);
        assertThat(p.getParticipantTypeCount()).isEqualTo(1);
        assertThat(p.getParticipantType()).isEqualTo("TRANSFER");
    }

    @Test
    void reflectsSuspendedStatus() {
        UserEntity toSuspend = user("acct-suspended");
        toSuspend.setAccountStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(toSuspend);

        AccountProjection p =
                users.findAccountProjectionByOidcSubject("acct-suspended").orElseThrow();

        assertThat(p.getAccountStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void reflectsDeletedAt() {
        UserEntity toDelete = user("acct-deleted");
        toDelete.setDeletedAt(java.time.Instant.now());
        users.saveAndFlush(toDelete);

        AccountProjection p =
                users.findAccountProjectionByOidcSubject("acct-deleted").orElseThrow();

        assertThat(p.getDeletedAt()).isNotNull();
    }

    @Test
    void accountResolver_issuesExactlyOnePreparedStatement_toResolveAnIdentity() {
        UserEntity saved = users.saveAndFlush(user("acct-stats"));
        userRoles.saveAndFlush(new UserRoleEntity(saved.getId(), UserRole.GUIDE));
        guideProfiles.saveAndFlush(guideProfileFor(saved.getId()));

        Statistics stats =
                entityManager
                        .getEntityManagerFactory()
                        .unwrap(SessionFactory.class)
                        .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("acct-stats").build();
        AccountResolution result = new AccountResolver(users).resolveAuthenticatedIdentity(jwt);

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void guideProfilesUserId_hasUniqueConstraint_rejectsDuplicateInsert() {
        UserEntity saved = users.saveAndFlush(user("acct-dup-guide"));
        guideProfiles.saveAndFlush(guideProfileFor(saved.getId()));

        assertThatThrownBy(() -> guideProfiles.saveAndFlush(guideProfileFor(saved.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void participantProfilesUserId_hasUniqueConstraint_rejectsDuplicateInsert() {
        UserEntity saved = users.saveAndFlush(user("acct-dup-participant"));
        participantProfiles.saveAndFlush(
                participantProfileFor(saved.getId(), ParticipantType.TRANSFER));

        assertThatThrownBy(
                        () ->
                                participantProfiles.saveAndFlush(
                                        participantProfileFor(
                                                saved.getId(), ParticipantType.HIGH_SCHOOL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
