package com.CampusToursLive.domain.onboarding;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.NotFoundException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Dedicated WRITE-SIDE reader (CTL-97 Core-B Task 5) that loads an account's CURRENT roles and both
 * profiles fresh from the database, for {@link #loadState(UUID)} callers that already hold the
 * account's advisory lock.
 *
 * <p>Intended call site: {@code OnboardingService}'s single {@code @Transactional} onboarding
 * write, AFTER it has acquired the account's {@code OidcIdentityLock}. Re-reading state under that
 * lock is what lets the I9/I13 eligibility checks that follow see up-to-date roles/profiles rather
 * than whatever snapshot was taken before the lock was held.
 *
 * <p>Deliberately does <b>not</b> reuse Core-A's {@link
 * com.CampusToursLive.security.AccountResolver}: that resolver is the READ-side, unlocked,
 * single-snapshot path used to classify an already authenticated request from one native-query
 * projection keyed by OIDC subject. Reusing it here — on the write side, after the lock — would
 * reintroduce exactly the kind of stale-read race the lock exists to close, since nothing would
 * force that resolver's read to happen strictly after lock acquisition. This reader instead issues
 * plain {@code findByUserId}/{@code findById} lookups keyed by the already-resolved account id,
 * which {@code ArchitectureGuardTest} allows (only the raw by-subject lookup is gated).
 */
@Component
public class LockedOnboardingStateReader {

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final GuideProfileRepository guideProfiles;
    private final ParticipantProfileRepository participantProfiles;

    public LockedOnboardingStateReader(
            UserRepository users,
            UserRoleRepository userRoles,
            GuideProfileRepository guideProfiles,
            ParticipantProfileRepository participantProfiles) {
        this.users = users;
        this.userRoles = userRoles;
        this.guideProfiles = guideProfiles;
        this.participantProfiles = participantProfiles;
    }

    /**
     * Reads fresh roles + both profiles for {@code userId}. Callers MUST already hold the account's
     * advisory {@code OidcIdentityLock} before calling this — see the class javadoc.
     *
     * @throws NotFoundException if no user row exists for {@code userId}
     */
    public LockedOnboardingState loadState(UUID userId) {
        UserEntity user =
                users.findById(userId)
                        .orElseThrow(() -> new NotFoundException("No user found for id " + userId));

        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (UserRoleEntity roleEntity : userRoles.findByUserId(userId)) {
            roles.add(roleEntity.getRole());
        }

        Optional<GuideProfileEntity> guideProfile = guideProfiles.findByUserId(userId);
        Optional<ParticipantProfileEntity> participantProfile =
                participantProfiles.findByUserId(userId);

        return new LockedOnboardingState(user, roles, guideProfile, participantProfile);
    }
}
