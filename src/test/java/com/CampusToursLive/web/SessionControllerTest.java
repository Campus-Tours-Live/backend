package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.ActiveRoleService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ActiveRoleRequest;
import com.CampusToursLive.web.dto.MeResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SessionController.userinfo / resolveSession — the principal view (MeResponse). Verifies the
 * enrichment in {@code me()}: identity nested under {@code user} and the authoritative role set
 * (sorted). Role-scoped fields (participantType/guideStatus) are no longer part of this contract.
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock CurrentUser currentUser;
    @Mock UserRoleRepository userRoles;
    @Mock ActiveRoleService activeRole;

    private SessionController controller() {
        return new SessionController(currentUser, userRoles, activeRole);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setLastActiveRole(UserRole.GUIDE);
        return u;
    }

    @Test
    void userinfo_returnsUserEnvelopeWithSortedRoles() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        // intentionally out of order — me() sorts
        when(userRoles.findByUserId(uid))
                .thenReturn(
                        List.of(
                                new UserRoleEntity(uid, UserRole.PARTICIPANT),
                                new UserRoleEntity(uid, UserRole.GUIDE)));

        MeResponse me = controller().userinfo().data();

        assertNotNull(me.user());
        assertEquals(uid.toString(), me.user().id());
        assertEquals(List.of("GUIDE", "PARTICIPANT"), me.roles());
        assertEquals("GUIDE", me.activeRole());
    }

    @Test
    void resolveSession_delegatesToResolveWithIntent() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.resolve("signup")).thenReturn(u);
        when(userRoles.findByUserId(uid)).thenReturn(List.of());

        MeResponse me = controller().resolveSession("signup").data();

        assertEquals(List.of(), me.roles());
        assertNotNull(me.user());
    }

    @Test
    void setActiveRole_delegatesToServiceThenReturnsMe() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        when(userRoles.findByUserId(uid))
                .thenReturn(List.of(new UserRoleEntity(uid, UserRole.GUIDE)));

        MeResponse me = controller().setActiveRole(new ActiveRoleRequest("GUIDE")).data();

        verify(activeRole).switchActiveRole(u, "GUIDE");
        assertEquals(List.of("GUIDE"), me.roles());
    }
}
