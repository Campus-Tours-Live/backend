package com.CampusToursLive.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.audit.AuditWriter;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.OnboardingAccountRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.security.OidcIdentity;
import com.CampusToursLive.security.OidcIdentityLock;
import com.CampusToursLive.security.UserProvisioningService;
import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * OnboardingService — CTL-97 Core-B Task 6, the single onboarding transaction. Unit-level (all
 * collaborators mocked): pins the error precedence (integrity &rarr; already-held &rarr;
 * eligibility), the {@code accountCreated} flag driving both the integrity skip (I12) and the
 * conditional {@code ACCOUNT_PROVISIONED} audit, and that the response is built from the fresh
 * post-mutation locked state. Atomicity/rollback and the real advisory lock are covered separately
 * by {@code OnboardingServiceIntegrationTest} (Testcontainers).
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock OidcIdentityLock identityLock;
    @Mock OnboardingAccountRepository onboardingAccounts;
    @Mock UserProvisioningService provisioning;
    @Mock LockedOnboardingStateReader stateReader;
    @Mock AuditWriter auditWriter;
    @Mock GuideService guideService;
    @Mock ParticipantService participantService;

    private OnboardingService service() {
        return new OnboardingService(
                identityLock,
                onboardingAccounts,
                provisioning,
                stateReader,
                auditWriter,
                guideService,
                participantService);
    }

    private static final String ISSUER = "https://accounts.google.com";

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(subject)
                .build();
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setOidcSubject("sub-" + id);
        u.setEmail("user-" + id + "@example.com");
        u.setDisplayName("Test User");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return u;
    }

    private static GuideProfileEntity guideProfile(UUID id) {
        GuideProfileEntity p = new GuideProfileEntity();
        p.setId(id);
        return p;
    }

    private static ParticipantProfileEntity participantProfile(UUID id, ParticipantType type) {
        ParticipantProfileEntity p = new ParticipantProfileEntity();
        p.setId(id);
        p.setParticipantType(type);
        return p;
    }

    private static LockedOnboardingState state(
            UserEntity user,
            Set<UserRole> roles,
            GuideProfileEntity guideProfile,
            ParticipantProfileEntity participantProfile) {
        return new LockedOnboardingState(
                user,
                roles,
                Optional.ofNullable(guideProfile),
                Optional.ofNullable(participantProfile));
    }

    private static GuideOnboardingRequest guideRequest() {
        return new GuideOnboardingRequest(
                "Maya",
                "Chen",
                UUID.randomUUID().toString(),
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
                List.of("166683"),
                List.of("DORM_HOUSING"),
                "en-US",
                "America/New_York",
                null);
    }

    private static GuideProfileResponse guideProfileResponse() {
        return new GuideProfileResponse("PENDING", List.of(), "bio", List.of("en-US"), List.of());
    }

    private static ParticipantProfileResponse participantProfileResponse() {
        return new ParticipantProfileResponse(
                "VERIFIED",
                "PROSPECTIVE",
                null,
                null,
                false,
                List.of(),
                List.of(),
                null,
                "en-US",
                "America/New_York");
    }

    // ---- onboardGuide: happy paths ---------------------------------------------------------

    @Test
    void onboardGuide_newIdentity_createsAccountGrantsRoleAndAuditsBoth() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity created = user(userId);
        Jwt jwt = jwt("new-subject");

        when(onboardingAccounts.findAnyByOidcSubject("new-subject")).thenReturn(Optional.empty());
        when(provisioning.provisionFromJwt(jwt)).thenReturn(created);
        // Pre-mutation read: a freshly-provisioned row is roleless — if the integrity check ran
        // on this (it must not, per I12), it would throw ConflictException and fail this test.
        LockedOnboardingState preState = state(created, EnumSet.noneOf(UserRole.class), null, null);
        LockedOnboardingState postState =
                state(created, EnumSet.of(UserRole.GUIDE), guideProfile(profileId), null);
        when(stateReader.loadState(userId)).thenReturn(preState, postState);
        GuideProfileResponse profileResponse = guideProfileResponse();
        when(guideService.updateProfile(eq(created), any())).thenReturn(profileResponse);

        OnboardingResponse response = service().onboardGuide(jwt, guideRequest());

        ArgumentCaptor<OidcIdentity> identityCaptor = ArgumentCaptor.forClass(OidcIdentity.class);
        verify(identityLock).acquire(identityCaptor.capture());
        assertThat(identityCaptor.getValue()).isEqualTo(new OidcIdentity(ISSUER, "new-subject"));

        verify(auditWriter)
                .record(
                        "ACCOUNT_PROVISIONED",
                        "user",
                        userId.toString(),
                        userId,
                        Map.of("accountCreated", true));
        verify(auditWriter)
                .record(
                        "ROLE_ACQUIRED",
                        "user",
                        userId.toString(),
                        userId,
                        Map.of(
                                "role",
                                "GUIDE",
                                "profileId",
                                profileId.toString(),
                                "accountCreated",
                                true));

        assertThat(response.acquiredRole()).isEqualTo(UserRole.GUIDE);
        assertThat(response.roles()).containsExactly(UserRole.GUIDE);
        assertThat(response.profile()).isSameAs(profileResponse);
        assertThat(response.accountState()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.user().id()).isEqualTo(userId.toString());
    }

    @Test
    void onboardGuide_existingAccount_doesNotAuditAccountProvisioned() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("existing-subject");

        when(onboardingAccounts.findAnyByOidcSubject("existing-subject"))
                .thenReturn(Optional.of(existingUser));
        ParticipantProfileEntity prospective =
                participantProfile(UUID.randomUUID(), ParticipantType.PROSPECTIVE);
        LockedOnboardingState preState =
                state(existingUser, EnumSet.of(UserRole.PARTICIPANT), null, prospective);
        LockedOnboardingState postState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.PARTICIPANT, UserRole.GUIDE),
                        guideProfile(profileId),
                        prospective);
        when(stateReader.loadState(userId)).thenReturn(preState, postState);
        when(guideService.updateProfile(eq(existingUser), any()))
                .thenReturn(guideProfileResponse());

        OnboardingResponse response = service().onboardGuide(jwt, guideRequest());

        verify(provisioning, never()).provisionFromJwt(any());
        verify(auditWriter, never())
                .record(
                        org.mockito.ArgumentMatchers.eq("ACCOUNT_PROVISIONED"),
                        anyString(),
                        anyString(),
                        any(),
                        any());
        verify(auditWriter, times(1))
                .record(
                        org.mockito.ArgumentMatchers.eq("ROLE_ACQUIRED"),
                        anyString(),
                        anyString(),
                        any(),
                        any());
        assertThat(response.roles()).containsExactly(UserRole.PARTICIPANT, UserRole.GUIDE);
    }

    // ---- onboardGuide: error precedence ----------------------------------------------------

    @Test
    void onboardGuide_roleAlreadyHeld_throwsConflict_noProfileCreation() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("held-subject");
        when(onboardingAccounts.findAnyByOidcSubject("held-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.GUIDE),
                        guideProfile(UUID.randomUUID()),
                        null);
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_ALREADY_GRANTED"));

        verifyNoInteractions(guideService);
        verifyNoInteractions(auditWriter);
    }

    @Test
    void onboardGuide_parentParticipant_throwsRoleNotEligible_missingProfileIsNotParent() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("parent-subject");
        when(onboardingAccounts.findAnyByOidcSubject("parent-subject"))
                .thenReturn(Optional.of(existingUser));
        ParticipantProfileEntity parent =
                participantProfile(UUID.randomUUID(), ParticipantType.PARENT);
        LockedOnboardingState preState =
                state(existingUser, EnumSet.of(UserRole.PARTICIPANT), null, parent);
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_NOT_ELIGIBLE"));

        verifyNoInteractions(guideService);
    }

    @Test
    void onboardGuide_noParticipantProfile_isNotTreatedAsParent_eligibilityPasses() {
        // An existing account holding a non-profile-backed role (ADMIN) so the (a) integrity
        // check passes (nonempty roles, no profile-backed role/profile mismatch), with NO
        // participant_profiles row at all — proving a genuinely absent profile is never read as
        // "not eligible" (I9): it must be treated as simply not-PARENT, not blocked.
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("no-participant-subject");
        when(onboardingAccounts.findAnyByOidcSubject("no-participant-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(existingUser, EnumSet.of(UserRole.ADMIN), null, null);
        LockedOnboardingState postState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.ADMIN, UserRole.GUIDE),
                        guideProfile(profileId),
                        null);
        when(stateReader.loadState(userId)).thenReturn(preState, postState);
        when(guideService.updateProfile(eq(existingUser), any()))
                .thenReturn(guideProfileResponse());

        OnboardingResponse response = service().onboardGuide(jwt, guideRequest());

        assertThat(response.acquiredRole()).isEqualTo(UserRole.GUIDE);
        // Fixed enum order (PARTICIPANT, GUIDE, ADMIN, SUPPORT), not insertion order.
        assertThat(response.roles()).containsExactly(UserRole.GUIDE, UserRole.ADMIN);
    }

    @Test
    void onboardGuide_existingAccountRoleless_throwsAccountStateInvalid() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("roleless-subject");
        when(onboardingAccounts.findAnyByOidcSubject("roleless-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(existingUser, EnumSet.noneOf(UserRole.class), null, null);
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ACCOUNT_STATE_INVALID"));
        verifyNoInteractions(guideService);
    }

    @Test
    void onboardGuide_existingAccountBrokenPairing_takesPrecedenceOverAlreadyHeld() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("broken-pairing-subject");
        when(onboardingAccounts.findAnyByOidcSubject("broken-pairing-subject"))
                .thenReturn(Optional.of(existingUser));
        // GUIDE role held but no guide_profiles row — a broken pairing. Also technically
        // "already held", but integrity (a) must fire before already-held (b).
        LockedOnboardingState preState =
                state(existingUser, EnumSet.of(UserRole.GUIDE), null, null);
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_PROFILE_STATE_INVALID"));
    }

    @Test
    void onboardGuide_accountDeletedViaDeletedAt_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        existingUser.setDeletedAt(Instant.now());
        Jwt jwt = jwt("deleted-subject");
        when(onboardingAccounts.findAnyByOidcSubject("deleted-subject"))
                .thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ACCOUNT_DELETED"));
        verifyNoInteractions(provisioning, stateReader, guideService, auditWriter);
    }

    @Test
    void onboardGuide_accountDeletedViaStatus_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        existingUser.setAccountStatus(AccountStatus.DELETED);
        Jwt jwt = jwt("deleted-status-subject");
        when(onboardingAccounts.findAnyByOidcSubject("deleted-status-subject"))
                .thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ACCOUNT_DELETED"));
    }

    @Test
    void onboardGuide_accountSuspended_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        existingUser.setAccountStatus(AccountStatus.SUSPENDED);
        Jwt jwt = jwt("suspended-subject");
        when(onboardingAccounts.findAnyByOidcSubject("suspended-subject"))
                .thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> service().onboardGuide(jwt, guideRequest()))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ACCOUNT_SUSPENDED"));
        verifyNoInteractions(stateReader, guideService, auditWriter);
    }

    // ---- onboardParticipant -----------------------------------------------------------------

    @Test
    void onboardParticipant_newIdentity_createsAccountGrantsRoleAndAuditsBoth() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity created = user(userId);
        Jwt jwt = jwt("new-participant-subject");

        when(onboardingAccounts.findAnyByOidcSubject("new-participant-subject"))
                .thenReturn(Optional.empty());
        when(provisioning.provisionFromJwt(jwt)).thenReturn(created);
        LockedOnboardingState preState = state(created, EnumSet.noneOf(UserRole.class), null, null);
        LockedOnboardingState postState =
                state(
                        created,
                        EnumSet.of(UserRole.PARTICIPANT),
                        null,
                        participantProfile(profileId, ParticipantType.PROSPECTIVE));
        when(stateReader.loadState(userId)).thenReturn(preState, postState);
        ParticipantProfileResponse profileResponse = participantProfileResponse();
        when(participantService.updateProfile(eq(created), any(OidcIdentity.class), any()))
                .thenReturn(profileResponse);

        OnboardingResponse response =
                service().onboardParticipant(jwt, participantRequest("PROSPECTIVE"));

        // I14: onboardParticipant passes its own JWT-derived OidcIdentity straight into
        // participantService.updateProfile — re-entrant with the lock resolveAccount() already
        // acquired for the SAME identity.
        ArgumentCaptor<OidcIdentity> participantIdentityCaptor =
                ArgumentCaptor.forClass(OidcIdentity.class);
        verify(participantService)
                .updateProfile(eq(created), participantIdentityCaptor.capture(), any());
        assertThat(participantIdentityCaptor.getValue())
                .isEqualTo(new OidcIdentity(ISSUER, "new-participant-subject"));

        verify(auditWriter)
                .record(
                        "ACCOUNT_PROVISIONED",
                        "user",
                        userId.toString(),
                        userId,
                        Map.of("accountCreated", true));
        verify(auditWriter)
                .record(
                        "ROLE_ACQUIRED",
                        "user",
                        userId.toString(),
                        userId,
                        Map.of(
                                "role",
                                "PARTICIPANT",
                                "profileId",
                                profileId.toString(),
                                "accountCreated",
                                true));
        assertThat(response.acquiredRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(response.profile()).isSameAs(profileResponse);
    }

    @Test
    void onboardParticipant_alreadyHeld_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("participant-held-subject");
        when(onboardingAccounts.findAnyByOidcSubject("participant-held-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.PARTICIPANT),
                        null,
                        participantProfile(UUID.randomUUID(), ParticipantType.PROSPECTIVE));
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(
                        () -> service().onboardParticipant(jwt, participantRequest("PROSPECTIVE")))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_ALREADY_GRANTED"));
        verifyNoInteractions(participantService);
    }

    @Test
    void onboardParticipant_parentWhileGuideHeld_throwsRoleNotEligible() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("guide-becoming-parent-subject");
        when(onboardingAccounts.findAnyByOidcSubject("guide-becoming-parent-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.GUIDE),
                        guideProfile(UUID.randomUUID()),
                        null);
        when(stateReader.loadState(userId)).thenReturn(preState);

        assertThatThrownBy(() -> service().onboardParticipant(jwt, participantRequest("PARENT")))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_NOT_ELIGIBLE"));
        verifyNoInteractions(participantService);
    }

    @Test
    void onboardParticipant_parentButNoGuideRole_isAllowed() {
        // An existing account holding a non-profile-backed role (ADMIN, so (a) integrity
        // passes) but no GUIDE — the PARENT<->GUIDE exclusion (I13) must only block when GUIDE
        // is actually held.
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity existingUser = user(userId);
        Jwt jwt = jwt("parent-no-guide-subject");
        when(onboardingAccounts.findAnyByOidcSubject("parent-no-guide-subject"))
                .thenReturn(Optional.of(existingUser));
        LockedOnboardingState preState =
                state(existingUser, EnumSet.of(UserRole.ADMIN), null, null);
        LockedOnboardingState postState =
                state(
                        existingUser,
                        EnumSet.of(UserRole.ADMIN, UserRole.PARTICIPANT),
                        null,
                        participantProfile(profileId, ParticipantType.PARENT));
        when(stateReader.loadState(userId)).thenReturn(preState, postState);
        when(participantService.updateProfile(eq(existingUser), any(OidcIdentity.class), any()))
                .thenReturn(participantProfileResponse());

        OnboardingResponse response =
                service().onboardParticipant(jwt, participantRequest("PARENT"));

        assertThat(response.acquiredRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(response.roles()).containsExactly(UserRole.PARTICIPANT, UserRole.ADMIN);
    }
}
