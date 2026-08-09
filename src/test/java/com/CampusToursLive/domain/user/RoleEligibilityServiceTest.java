package com.CampusToursLive.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.web.dto.RoleEligibilityResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RoleEligibilityService.checkEligibility — authoritative "can this account acquire this role"
 * check backing GET /users/me/role-eligibility. Covers: disabled/suspended account -> 403
 * ACCOUNT_NOT_ACTIVE (never eligible=false); already-held role -> ROLE_ALREADY_HELD; GUIDE denied
 * to a PARENT-type participant; GUIDE allowed to a non-PARENT / no-profile-yet participant;
 * PARTICIPANT is always eligible unless already held.
 */
@ExtendWith(MockitoExtension.class)
class RoleEligibilityServiceTest {

    @Mock UserRoleRepository userRoles;
    @Mock ParticipantProfileRepository participantProfiles;

    private RoleEligibilityService service() {
        return new RoleEligibilityService(userRoles, participantProfiles);
    }

    private static UserEntity user(UUID id, AccountStatus status) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setAccountStatus(status);
        return u;
    }

    @Test
    void throwsForbidden_withAccountNotActiveCode_whenAccountNotActive() {
        UserEntity u = user(UUID.randomUUID(), AccountStatus.SUSPENDED);

        ForbiddenException ex =
                assertThrows(
                        ForbiddenException.class,
                        () -> service().checkEligibility(u, UserRole.GUIDE));
        assertEquals("ACCOUNT_NOT_ACTIVE", ex.getMessage());
    }

    @Test
    void ineligible_roleAlreadyHeld_takesPriorityOverParentCheck() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid, AccountStatus.ACTIVE);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        RoleEligibilityResponse resp = service().checkEligibility(u, UserRole.GUIDE);

        assertEquals(false, resp.eligible());
        assertEquals(RoleIneligibilityReason.ROLE_ALREADY_HELD, resp.reason());
    }

    @Test
    void ineligible_guide_whenParticipantIsParentType() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid, AccountStatus.ACTIVE);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);
        ParticipantProfileEntity profile = new ParticipantProfileEntity();
        profile.setParticipantType(ParticipantType.PARENT);
        when(participantProfiles.findByUserId(uid)).thenReturn(Optional.of(profile));

        RoleEligibilityResponse resp = service().checkEligibility(u, UserRole.GUIDE);

        assertEquals(false, resp.eligible());
        assertEquals(RoleIneligibilityReason.PARENT_CANNOT_BECOME_GUIDE, resp.reason());
    }

    @Test
    void eligible_guide_whenParticipantIsNotParentType() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid, AccountStatus.ACTIVE);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);
        ParticipantProfileEntity profile = new ParticipantProfileEntity();
        profile.setParticipantType(ParticipantType.PROSPECTIVE);
        when(participantProfiles.findByUserId(uid)).thenReturn(Optional.of(profile));

        RoleEligibilityResponse resp = service().checkEligibility(u, UserRole.GUIDE);

        assertTrue(resp.eligible());
        assertNull(resp.reason());
    }

    @Test
    void eligible_guide_whenNoParticipantProfileYet() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid, AccountStatus.ACTIVE);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);
        when(participantProfiles.findByUserId(uid)).thenReturn(Optional.empty());

        RoleEligibilityResponse resp = service().checkEligibility(u, UserRole.GUIDE);

        assertTrue(resp.eligible());
        assertNull(resp.reason());
    }

    @Test
    void eligible_participant_whenNotAlreadyHeld() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid, AccountStatus.ACTIVE);
        when(userRoles.existsByUserIdAndRole(uid, UserRole.PARTICIPANT)).thenReturn(false);

        RoleEligibilityResponse resp = service().checkEligibility(u, UserRole.PARTICIPANT);

        assertTrue(resp.eligible());
        assertNull(resp.reason());
    }
}
