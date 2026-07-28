package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.RoleEligibilityService;
import com.CampusToursLive.domain.user.RoleIneligibilityReason;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.web.dto.CurrentUserResponse;
import com.CampusToursLive.web.dto.RoleEligibilityResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * SessionController.me / resolveSession / roleEligibility — the principal view
 * (CurrentUserResponse) and the role-eligibility passthrough. Verifies the enrichment in {@code
 * currentUser()}: identity nested under {@code user} and the authoritative role set in FIXED enum
 * order (PARTICIPANT, GUIDE, ADMIN, SUPPORT), not insertion order. There is no {@code currentRole}
 * field and no /session/current-role endpoint any more — current-role/session context is bff-owned.
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock CurrentUser currentUser;
    @Mock UserRoleRepository userRoles;
    @Mock RoleEligibilityService roleEligibilityService;

    private SessionController controller() {
        return new SessionController(currentUser, userRoles, roleEligibilityService);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static ProvisionedAccount provisionedAccount(UUID id) {
        return new ProvisionedAccount(
                id,
                "sub-1",
                "ada@example.com",
                "Ada",
                "Lovelace",
                "Ada Lovelace",
                AccountStatus.ACTIVE,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Set.of());
    }

    @Test
    void me_returnsUserEnvelopeWithFixedEnumOrderRoles() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        // GUIDE (ordinal 1) inserted before PARTICIPANT (ordinal 0) — currentUser() must
        // still emit fixed enum order, not insertion order.
        when(userRoles.findByUserId(uid))
                .thenReturn(
                        List.of(
                                new UserRoleEntity(uid, UserRole.GUIDE),
                                new UserRoleEntity(uid, UserRole.PARTICIPANT)));

        CurrentUserResponse body = controller().me().data();

        assertNotNull(body.user());
        assertEquals(uid.toString(), body.user().id());
        assertEquals(List.of(UserRole.PARTICIPANT, UserRole.GUIDE), body.roles());
    }

    @Test
    void me_returnsEmptyRoles_forBrandNewSignup() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        when(userRoles.findByUserId(uid)).thenReturn(List.of());

        CurrentUserResponse body = controller().me().data();

        assertEquals(List.of(), body.roles());
    }

    @Test
    void resolveSession_delegatesToResolveWithIntent() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.resolve("signup")).thenReturn(u);
        when(userRoles.findByUserId(uid)).thenReturn(List.of());

        CurrentUserResponse body = controller().resolveSession("signup").data();

        assertEquals(List.of(), body.roles());
        assertNotNull(body.user());
    }

    @Test
    void roleEligibility_delegatesParsedRoleToService() {
        UUID uid = UUID.randomUUID();
        when(currentUser.requireProvisioned()).thenReturn(provisionedAccount(uid));
        when(roleEligibilityService.checkEligibility(any(UserEntity.class), eq(UserRole.GUIDE)))
                .thenReturn(
                        new RoleEligibilityResponse(
                                false, RoleIneligibilityReason.PARENT_CANNOT_BECOME_GUIDE));

        RoleEligibilityResponse body = controller().roleEligibility("GUIDE").data();

        assertEquals(false, body.eligible());
        assertEquals(RoleIneligibilityReason.PARENT_CANNOT_BECOME_GUIDE, body.reason());
    }

    @Test
    void roleEligibility_eligibleHasNullReason() {
        UUID uid = UUID.randomUUID();
        when(currentUser.requireProvisioned()).thenReturn(provisionedAccount(uid));
        when(roleEligibilityService.checkEligibility(
                        any(UserEntity.class), eq(UserRole.PARTICIPANT)))
                .thenReturn(new RoleEligibilityResponse(true, null));

        RoleEligibilityResponse body = controller().roleEligibility("PARTICIPANT").data();

        assertEquals(true, body.eligible());
        assertNull(body.reason());
    }

    @Test
    void roleEligibility_throws400_whenRoleBlank() {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class, () -> controller().roleEligibility("  "));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void roleEligibility_throws400_whenRoleUnknown() {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class, () -> controller().roleEligibility("BOGUS"));
        assertEquals(400, ex.getStatusCode().value());
    }
}
