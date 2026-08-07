package com.CampusToursLive.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideEarningsService;
import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.GuideProfileSnapshot;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.security.RoleAccountContext;
import com.CampusToursLive.web.dto.GuideEarningsResponse;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
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
 * GuideController — thin adapter over the typed role contexts (CTL-97 Core-A Task 5, Core-B Task
 * 8): both {@code GET} and {@code PATCH} gate via {@link CurrentUser#requireGuide()} (pending ->
 * 404 ACCOUNT_NOT_PROVISIONED, provisioned without GUIDE -> 403 ROLE_REQUIRED, GUIDE held but
 * profile missing -> 409 ROLE_PROFILE_STATE_INVALID, GUIDE held + profile present -> 200). {@code
 * PATCH} is EDIT-ONLY (Core-B Task 8): role acquisition now happens exclusively via {@code
 * OnboardingService} (POST /v1/users/me/roles/guide); PATCH re-loads the managed {@link UserEntity}
 * by {@code account.userId()} and forces {@code submit=false} on the request before delegating to
 * {@code GuideService.updateProfile}, so it can never grant a role or reset {@code guide_status}
 * back to PENDING.
 */
@ExtendWith(MockitoExtension.class)
class GuideControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock CurrentUser currentUser;
    @Mock GuideService guideService;
    @Mock GuideEarningsService guideEarningsService;
    @Mock UserRepository users;

    private GuideController controller() {
        return new GuideController(currentUser, guideService, guideEarningsService, users);
    }

    private static GuideProfileResponse response() {
        return new GuideProfileResponse(null, null, null, null, null, null, 0);
    }

    private static GuideEarningsResponse earningsResponse() {
        return new GuideEarningsResponse(0L, 0L, "USD");
    }

    private static ProvisionedAccount account(UserRole... roles) {
        return new ProvisionedAccount(
                USER_ID,
                "sub-1",
                "guide@example.com",
                "Ada",
                "Lovelace",
                "Ada Lovelace",
                AccountStatus.ACTIVE,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Set.of(roles));
    }

    private static GuideProfileSnapshot snapshot() {
        return new GuideProfileSnapshot(
                UUID.randomUUID(),
                USER_ID,
                "bio",
                "[\"en-US\"]",
                "[\"GENERAL_CAMPUS\"]",
                GuideStatus.VERIFIED,
                Instant.now(),
                Instant.now());
    }

    // ---- GET /guide/profile ---------------------------------------------------------------

    @Test
    void getProfile_holderWithProfile_buildsResponseFromSnapshot() {
        GuideProfileSnapshot snapshot = snapshot();
        RoleAccountContext.Guide ctx =
                new RoleAccountContext.Guide(account(UserRole.GUIDE), snapshot);
        when(currentUser.requireGuide()).thenReturn(ctx);
        GuideProfileResponse resp = response();
        when(guideService.getProfile(snapshot)).thenReturn(resp);

        assertSame(resp, controller().getProfile().data());
    }

    @Test
    void getProfile_pending_propagates404AccountNotProvisioned() {
        when(currentUser.requireGuide())
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
    void getProfile_provisionedWithoutGuideRole_propagates403RoleRequired() {
        when(currentUser.requireGuide())
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: GUIDE",
                                "ROLE_REQUIRED",
                                Map.of("role", "GUIDE")));

        assertThatThrownBy(() -> controller().getProfile())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
    }

    @Test
    void getProfile_guideRoleHeldButProfileMissing_propagates409RoleProfileStateInvalid() {
        when(currentUser.requireGuide())
                .thenThrow(ConflictException.roleProfileStateInvalid("GUIDE"));

        assertThatThrownBy(() -> controller().getProfile())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_PROFILE_STATE_INVALID"));
    }

    // ---- PATCH /guide/profile (Core-B Task 8: edit-only) -----------------------------------

    @Test
    void updateProfile_holder_loadsManagedEntityAndDelegatesAsEditOnly() {
        RoleAccountContext.Guide ctx =
                new RoleAccountContext.Guide(account(UserRole.GUIDE), snapshot());
        when(currentUser.requireGuide()).thenReturn(ctx);
        UserEntity managed = new UserEntity();
        managed.setId(USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(managed));
        // Client sends submit=true (e.g. a stale/adversarial client) — PATCH must still edit-only.
        GuideProfileUpdateRequest req =
                new GuideProfileUpdateRequest(
                        "Maya", null, null, null, null, "bio", null, null, null, true, null, null);
        GuideProfileResponse resp = response();
        org.mockito.ArgumentCaptor<GuideProfileUpdateRequest> captor =
                org.mockito.ArgumentCaptor.forClass(GuideProfileUpdateRequest.class);
        when(guideService.updateProfile(org.mockito.ArgumentMatchers.eq(managed), captor.capture()))
                .thenReturn(resp);

        assertSame(resp, controller().updateProfile(req).data());

        GuideProfileUpdateRequest delegated = captor.getValue();
        assertThat(delegated.submit()).isFalse();
        assertThat(delegated.firstName()).isEqualTo("Maya");
        assertThat(delegated.bio()).isEqualTo("bio");
    }

    @Test
    void updateProfile_provisionedNonHolder_propagates403RoleRequired() {
        GuideProfileUpdateRequest req =
                new GuideProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null);
        when(currentUser.requireGuide())
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: GUIDE",
                                "ROLE_REQUIRED",
                                Map.of("role", "GUIDE")));

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
        GuideProfileUpdateRequest req =
                new GuideProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null);
        when(currentUser.requireGuide())
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
        RoleAccountContext.Guide ctx =
                new RoleAccountContext.Guide(account(UserRole.GUIDE), snapshot());
        when(currentUser.requireGuide()).thenReturn(ctx);
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        GuideProfileUpdateRequest req =
                new GuideProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> controller().updateProfile(req))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ACCOUNT_STATE_INVALID"));
    }

    @Test
    void getEarnings_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        GuideEarningsResponse earnings = earningsResponse();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(guideEarningsService.getEarnings(u)).thenReturn(earnings);

        assertSame(earnings, controller().getEarnings().data());
    }
}
