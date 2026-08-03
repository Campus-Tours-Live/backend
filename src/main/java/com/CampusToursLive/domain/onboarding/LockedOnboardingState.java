package com.CampusToursLive.domain.onboarding;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable write-side snapshot of one account's onboarding-relevant state, read by {@link
 * LockedOnboardingStateReader} AFTER {@code OnboardingService} has acquired the account's advisory
 * {@code OidcIdentityLock} inside its single {@code @Transactional} — see that reader's class
 * javadoc for why this is kept separate from Core-A's unlocked read-side {@code AccountResolver}.
 *
 * <p>{@code guideProfile}/{@code participantProfile} are {@link Optional} — never {@code null} —
 * specifically so the I9/I13 eligibility checks that consume this record can branch on presence
 * without null checks.
 */
public record LockedOnboardingState(
        UserEntity user,
        Set<UserRole> roles,
        Optional<GuideProfileEntity> guideProfile,
        Optional<ParticipantProfileEntity> participantProfile) {}
