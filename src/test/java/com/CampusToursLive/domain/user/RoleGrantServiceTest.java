package com.CampusToursLive.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * RoleGrantService.grant — idempotent role acquisition. Writing the user_roles row IS the grant; a
 * SWITCHABLE role also becomes the active context on first acquisition, while staff roles are
 * granted but never auto-activated. Re-granting an already-held role is a no-op.
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
        u.setLastActiveRole(UserRole.PARTICIPANT);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        service().grant(u, UserRole.GUIDE);

        verify(userRoles, never()).save(org.mockito.ArgumentMatchers.any());
        assertEquals(UserRole.PARTICIPANT, u.getLastActiveRole()); // unchanged
    }

    @Test
    void grant_insertsRowAndActivates_forSwitchableRole() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);

        service().grant(u, UserRole.GUIDE);

        ArgumentCaptor<UserRoleEntity> saved = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoles).save(saved.capture());
        assertEquals(UserRole.GUIDE, saved.getValue().getRole());
        assertEquals(UserRole.GUIDE, u.getLastActiveRole()); // first acquisition → active
    }

    @Test
    void grant_insertsRowButDoesNotActivate_forStaffRole() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.ADMIN)).thenReturn(false);

        service().grant(u, UserRole.ADMIN);

        verify(userRoles).save(org.mockito.ArgumentMatchers.any());
        // Staff roles are granted but must NOT become the active /dashboard context.
        assertNull(u.getLastActiveRole());
    }
}
