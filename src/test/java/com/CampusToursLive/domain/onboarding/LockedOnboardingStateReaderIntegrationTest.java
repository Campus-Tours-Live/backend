package com.CampusToursLive.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers proof for {@link LockedOnboardingStateReader#loadState(UUID)} — CTL-97 Core-B Task
 * 5. This is the WRITE-side reader {@code OnboardingService} will call AFTER acquiring the
 * account's advisory {@code OidcIdentityLock}, so these tests pin that {@code loadState} reflects
 * whatever is currently in the database (roles + both profiles), independent of Core-A's unlocked
 * read-side {@code AccountResolver}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LockedOnboardingStateReader.class)
class LockedOnboardingStateReaderIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private ParticipantProfileRepository participantProfiles;
    @Autowired private LockedOnboardingStateReader reader;

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

    @Test
    void loadState_returnsNoRolesAndEmptyProfiles_forFreshlyCreatedAccount() {
        UserEntity saved = users.saveAndFlush(user("sub-fresh-" + UUID.randomUUID()));

        LockedOnboardingState state = reader.loadState(saved.getId());

        assertThat(state.user().getId()).isEqualTo(saved.getId());
        assertThat(state.roles()).isEmpty();
        assertThat(state.guideProfile()).isEmpty();
        assertThat(state.participantProfile()).isEmpty();
    }

    @Test
    void loadState_returnsGuideRoleAndGuideProfilePresent_whenOnlyGuide() {
        UserEntity saved = users.saveAndFlush(user("sub-guide-" + UUID.randomUUID()));
        UUID userId = saved.getId();
        userRoles.saveAndFlush(new UserRoleEntity(userId, UserRole.GUIDE));
        GuideProfileEntity savedGuideProfile = guideProfiles.saveAndFlush(guideProfileFor(userId));

        LockedOnboardingState state = reader.loadState(userId);

        assertThat(state.roles()).containsExactly(UserRole.GUIDE);
        assertThat(state.guideProfile()).isPresent();
        assertThat(state.guideProfile().get().getId()).isEqualTo(savedGuideProfile.getId());
        assertThat(state.participantProfile()).isEmpty();
    }

    @Test
    void loadState_returnsBothProfilesPresent_whenUserHoldsBothRoles() {
        UserEntity saved = users.saveAndFlush(user("sub-both-" + UUID.randomUUID()));
        UUID userId = saved.getId();
        userRoles.saveAndFlush(new UserRoleEntity(userId, UserRole.GUIDE));
        userRoles.saveAndFlush(new UserRoleEntity(userId, UserRole.PARTICIPANT));
        GuideProfileEntity savedGuideProfile = guideProfiles.saveAndFlush(guideProfileFor(userId));
        ParticipantProfileEntity savedParticipantProfile =
                participantProfiles.saveAndFlush(participantProfileFor(userId));

        LockedOnboardingState state = reader.loadState(userId);

        assertThat(state.roles()).containsExactlyInAnyOrder(UserRole.GUIDE, UserRole.PARTICIPANT);
        assertThat(state.guideProfile()).isPresent();
        assertThat(state.guideProfile().get().getId()).isEqualTo(savedGuideProfile.getId());
        assertThat(state.participantProfile()).isPresent();
        assertThat(state.participantProfile().get().getId())
                .isEqualTo(savedParticipantProfile.getId());
    }

    @Test
    void loadState_throwsNotFoundException_whenNoUserExistsForId() {
        UUID missingUserId = UUID.randomUUID();

        assertThatThrownBy(() -> reader.loadState(missingUserId))
                .isInstanceOf(NotFoundException.class);
    }
}
