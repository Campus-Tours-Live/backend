package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.user.ActiveRoleService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ActiveRoleRequest;
import com.CampusToursLive.web.dto.MeResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SessionController.userinfo / resolveSession — the principal view (MeResponse). Verifies the
 * enrichment in {@code me()}: the authoritative role set (sorted), the participant type, and the
 * guide application status, all assembled from the per-role repositories.
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock CurrentUser currentUser;
    @Mock UserRoleRepository userRoles;
    @Mock ParticipantProfileRepository participants;
    @Mock GuideProfileRepository guides;
    @Mock ActiveRoleService activeRole;

    private SessionController controller() {
        return new SessionController(currentUser, userRoles, participants, guides, activeRole);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setLastActiveRole(UserRole.GUIDE);
        return u;
    }

    @Test
    void userinfo_returnsSortedRolesAndPerRoleStatus() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        // intentionally out of order — me() sorts
        when(userRoles.findByUserId(uid))
                .thenReturn(
                        List.of(
                                new UserRoleEntity(uid, UserRole.PARTICIPANT),
                                new UserRoleEntity(uid, UserRole.GUIDE)));
        ParticipantProfileEntity pp = new ParticipantProfileEntity();
        pp.setParticipantType(ParticipantType.PROSPECTIVE);
        when(participants.findByUserId(uid)).thenReturn(Optional.of(pp));
        GuideProfileEntity gp = new GuideProfileEntity();
        gp.setApplicationStatus(GuideApplicationStatus.PENDING_REVIEW);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(gp));

        MeResponse me = controller().userinfo().data();

        assertEquals(List.of("GUIDE", "PARTICIPANT"), me.roles());
        assertEquals("GUIDE", me.activeRole());
        assertEquals("PROSPECTIVE", me.participantType());
        assertEquals("PENDING_REVIEW", me.guideStatus());
    }

    @Test
    void resolveSession_delegatesToResolveWithIntent() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.resolve("signup")).thenReturn(u);
        when(userRoles.findByUserId(uid)).thenReturn(List.of());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        MeResponse me = controller().resolveSession("signup").data();

        assertEquals(List.of(), me.roles());
        assertEquals(null, me.participantType());
        assertEquals(null, me.guideStatus());
    }

    @Test
    void setActiveRole_delegatesToServiceThenReturnsMe() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(currentUser.require()).thenReturn(u);
        when(userRoles.findByUserId(uid))
                .thenReturn(List.of(new UserRoleEntity(uid, UserRole.GUIDE)));
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        MeResponse me = controller().setActiveRole(new ActiveRoleRequest("GUIDE")).data();

        verify(activeRole).switchActiveRole(u, "GUIDE");
        assertEquals(List.of("GUIDE"), me.roles());
    }
}
