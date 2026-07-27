package com.CampusToursLive.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RoleGrantService.grant — idempotent role acquisition. Writing the user_roles row IS the grant.
 * Current-role/session context is owned by the BFF session, not Core, so grant() never touches
 * {@code user}. Re-granting an already-held role is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class RoleGrantServiceTest {

    @Mock UserRoleRepository userRoles;

    private RoleGrantService service() {
        return new RoleGrantService(userRoles);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    @Test
    void grant_isNoOp_whenRoleAlreadyHeld() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        service().grant(u, UserRole.GUIDE);

        verify(userRoles, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void grant_insertsRow_forSwitchableRole() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);

        service().grant(u, UserRole.GUIDE);

        ArgumentCaptor<UserRoleEntity> saved = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoles).save(saved.capture());
        assertEquals(UserRole.GUIDE, saved.getValue().getRole());
        assertEquals(uid, saved.getValue().getUserId());
    }

    @Test
    void grant_insertsRow_forStaffRole() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.ADMIN)).thenReturn(false);

        service().grant(u, UserRole.ADMIN);

        ArgumentCaptor<UserRoleEntity> saved = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoles).save(saved.capture());
        assertEquals(UserRole.ADMIN, saved.getValue().getRole());
    }
}
