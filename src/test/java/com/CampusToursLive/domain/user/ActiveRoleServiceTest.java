package com.CampusToursLive.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ActiveRoleService.switchActiveRole — switch-active-role rule: null/blank → 422; an unknown enum
 * string → 422; a held-but-non-switchable staff role (ADMIN) → 403; a switchable role the user does
 * NOT hold → 403; a held switchable role → last_active_role updated + saved.
 */
@ExtendWith(MockitoExtension.class)
class ActiveRoleServiceTest {

    @Mock UserRepository users;
    @Mock UserRoleRepository userRoles;

    private ActiveRoleService service() {
        return new ActiveRoleService(users, userRoles);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    @Test
    void switch_throws422_whenRoleNullOrBlank() {
        assertThrows(
                ValidationException.class,
                () -> service().switchActiveRole(user(UUID.randomUUID()), null));
    }

    @Test
    void switch_throws422_whenRoleNotAnEnum() {
        assertThrows(
                ValidationException.class,
                () -> service().switchActiveRole(user(UUID.randomUUID()), "BOGUS"));
    }

    @Test
    void switch_throws403_forHeldButNonSwitchableStaffRole() {
        // ADMIN is not SWITCHABLE → rejected before user_roles is even consulted.
        assertThrows(
                ForbiddenException.class,
                () -> service().switchActiveRole(user(UUID.randomUUID()), "ADMIN"));
    }

    @Test
    void switch_throws403_whenSwitchableRoleNotHeld() {
        UUID uid = UUID.randomUUID();
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);
        assertThrows(
                ForbiddenException.class, () -> service().switchActiveRole(user(uid), "GUIDE"));
    }

    @Test
    void switch_updatesLastActiveRole_whenHeldAndSwitchable() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        service().switchActiveRole(u, "GUIDE");

        assertEquals(UserRole.GUIDE, u.getLastActiveRole());
        verify(users).save(u);
    }
}
