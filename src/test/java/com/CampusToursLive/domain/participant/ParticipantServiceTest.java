package com.CampusToursLive.domain.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ParticipantService — participant onboarding (updateProfile). Covers the PARTICIPANT-role grant,
 * participantType parsing, the bidirectional guide/parent exclusion, display-name syncing, and the
 * tolerant interests JSON (object form + legacy array form).
 */
@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock ParticipantProfileRepository profiles;
    @Mock UserRepository users;
    @Mock UserRoleRepository userRoles;
    @Mock RoleGrantService roleGrant;

    private ParticipantService service() {
        return new ParticipantService(profiles, users, userRoles, roleGrant, new ObjectMapper());
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static ParticipantProfileUpdateRequest req(
            String firstName, String displayName, String participantType, List<String> topics) {
        return new ParticipantProfileUpdateRequest(
                firstName,
                null,
                displayName,
                participantType,
                null,
                null,
                null,
                topics,
                null,
                null,
                null);
    }

    @Test
    void update_new_grantsParticipantRoleAndDefaultsType() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res =
                service().updateProfile(u, req("Jordan", null, null, null));

        assertEquals("PROSPECTIVE", res.participantType()); // created default
        verify(roleGrant).grant(u, UserRole.PARTICIPANT);
        verify(users).save(u);
        verify(profiles).save(any());
    }

    @Test
    void update_422_whenParticipantTypeInvalid() {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());
        var ex =
                assertThrows(
                        RuntimeException.class,
                        () -> service().updateProfile(user(uid), req(null, null, "BOGUS", null)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_422_whenParentButAlreadyAGuide() {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        var ex =
                assertThrows(
                        RuntimeException.class,
                        () -> service().updateProfile(user(uid), req(null, null, "PARENT", null)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_allowsParent_whenNotAGuide() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);

        ParticipantProfileResponse res =
                service().updateProfile(u, req(null, null, "PARENT", null));

        assertEquals("PARENT", res.participantType());
        verify(roleGrant).grant(u, UserRole.PARTICIPANT);
    }

    @Test
    void update_storesTopicsOfInterest() {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, List.of("DORM_HOUSING")));

        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
    }

    @Test
    void update_explicitDisplayNameWinsOverFirstLastSync() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res =
                service().updateProfile(u, req("Jordan", "JL the Guide", null, null));

        assertEquals("JL the Guide", res.displayName());
    }

    @Test
    void update_readsLegacyArrayInterests_whenTopicsNotResent() {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PROSPECTIVE);
        existing.setInterests("[\"GENERAL_CAMPUS\"]"); // legacy array form
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, null));

        assertEquals(List.of("GENERAL_CAMPUS"), res.topicsOfInterest());
    }
}
