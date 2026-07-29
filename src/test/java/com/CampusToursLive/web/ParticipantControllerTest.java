package com.CampusToursLive.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.ParticipantProfileSnapshot;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.security.RoleAccountContext;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ParticipantController — thin adapter over the typed role contexts (CTL-97 Core-A Task 5, Core-B
 * Task 8): both {@code GET} and {@code PATCH} gate via {@link CurrentUser#requireParticipant()}
 * (pending -> 404 ACCOUNT_NOT_PROVISIONED, provisioned without PARTICIPANT -> 403 ROLE_REQUIRED,
 * PARTICIPANT held but profile missing -> 409 ROLE_PROFILE_STATE_INVALID, held + profile present ->
 * 200) and then re-loads the managed {@link UserEntity} to delegate to the existing, unchanged
 * {@code ParticipantService} methods — the response also carries {@code preferredLanguage}/{@code
 * timezone}, which live on {@code users}, not on the {@link ProvisionedAccount} snapshot. {@code
 * PATCH} is EDIT-ONLY (Core-B Task 8): role acquisition now happens exclusively via {@code
 * OnboardingService} (POST /v1/users/me/roles/participant).
 */
@ExtendWith(MockitoExtension.class)
class ParticipantControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock CurrentUser currentUser;
    @Mock ParticipantService participantService;
    @Mock UserRepository users;

    private ParticipantController controller() {
        return new ParticipantController(currentUser, participantService, users);
    }

    private static ParticipantProfileResponse response() {
        return new ParticipantProfileResponse(
                "VERIFIED", null, null, null, null, null, null, null, null, null);
    }

    private static ProvisionedAccount account(UserRole... roles) {
        return new ProvisionedAccount(
                USER_ID,
                "sub-1",
                "participant@example.com",
                "Grace",
                "Hopper",
                "Grace Hopper",
                AccountStatus.ACTIVE,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Set.of(roles));
    }

    private static ParticipantProfileSnapshot snapshot() {
        return new ParticipantProfileSnapshot(
                UUID.randomUUID(),
                USER_ID,
                ParticipantType.PROSPECTIVE,
                "HIGH_SCHOOL_SENIOR",
                "Computer Science",
                "{}",
                false,
                Instant.now(),
                Instant.now());
    }

    // ---- GET /participant/profile ----------------------------------------------------------

    @Test
    void getProfile_holderWithProfile_gatesThenDelegatesUsingManagedEntity() {
        RoleAccountContext.Participant ctx =
                new RoleAccountContext.Participant(account(UserRole.PARTICIPANT), snapshot());
        when(currentUser.requireParticipant()).thenReturn(ctx);
        UserEntity managed = new UserEntity();
        managed.setId(USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(managed));
        ParticipantProfileResponse resp = response();
        when(participantService.getProfile(managed)).thenReturn(resp);

        assertSame(resp, controller().getProfile().data());
    }

    @Test
    void getProfile_pending_propagates404AccountNotProvisioned() {
        when(currentUser.requireParticipant())
                .thenThrow(
                        new NotFoundException(
                                "Account not provisioned", "ACCOUNT_NOT_PROVISIONED"));

        assertThatThrownBy(() -> controller().getProfile())
                .isInstanceOf(NotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((NotFoundException) ex).code())
                                        .isEqualTo("ACCOUNT_NOT_PROVISIONED"));
    }

    @Test
    void getProfile_provisionedWithoutParticipantRole_propagates403RoleRequired() {
        when(currentUser.requireParticipant())
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: PARTICIPANT",
                                "ROLE_REQUIRED",
                                Map.of("role", "PARTICIPANT")));

        assertThatThrownBy(() -> controller().getProfile())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
    }

    @Test
    void getProfile_participantRoleHeldButProfileMissing_propagates409RoleProfileStateInvalid() {
        when(currentUser.requireParticipant())
                .thenThrow(ConflictException.roleProfileStateInvalid("PARTICIPANT"));

        assertThatThrownBy(() -> controller().getProfile())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_PROFILE_STATE_INVALID"));
    }

    // ---- PATCH /participant/profile (Core-B Task 8: edit-only) -----------------------------

    @Test
    void updateProfile_holder_loadsManagedEntityAndDelegates() {
        RoleAccountContext.Participant ctx =
                new RoleAccountContext.Participant(account(UserRole.PARTICIPANT), snapshot());
        when(currentUser.requireParticipant()).thenReturn(ctx);
        UserEntity managed = new UserEntity();
        managed.setId(USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(managed));
        ParticipantProfileUpdateRequest req =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);
        ParticipantProfileResponse resp = response();
        when(participantService.updateProfile(managed, req)).thenReturn(resp);

        assertSame(resp, controller().updateProfile(req).data());
    }

    @Test
    void updateProfile_provisionedNonHolder_propagates403RoleRequired() {
        ParticipantProfileUpdateRequest req =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);
        when(currentUser.requireParticipant())
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: PARTICIPANT",
                                "ROLE_REQUIRED",
                                Map.of("role", "PARTICIPANT")));

        assertThatThrownBy(() -> controller().updateProfile(req))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
        verify(users, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateProfile_pending_propagates404AccountNotProvisioned() {
        ParticipantProfileUpdateRequest req =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);
        when(currentUser.requireParticipant())
                .thenThrow(
                        new NotFoundException(
                                "Account not provisioned", "ACCOUNT_NOT_PROVISIONED"));

        assertThatThrownBy(() -> controller().updateProfile(req))
                .isInstanceOf(NotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((NotFoundException) ex).code())
                                        .isEqualTo("ACCOUNT_NOT_PROVISIONED"));
        verify(users, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateProfile_managedUserMissing_throwsAccountStateInvalid() {
        RoleAccountContext.Participant ctx =
                new RoleAccountContext.Participant(account(UserRole.PARTICIPANT), snapshot());
        when(currentUser.requireParticipant()).thenReturn(ctx);
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        ParticipantProfileUpdateRequest req =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> controller().updateProfile(req))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ACCOUNT_STATE_INVALID"));
    }
}
