package com.CampusToursLive.domain.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.fasterxml.jackson.core.type.TypeReference;
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

    // ---- getProfile ----

    @Test
    void getProfile_existingProfile_returnsProfileFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.TRANSFER);
        existing.setGradeLevel("SOPHOMORE");
        existing.setIntendedMajor("CS");
        existing.setInterests("{\"topics\":[\"DORM_HOUSING\"]}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res = service().getProfile(u);

        assertEquals("TRANSFER", res.participantType());
        assertEquals("SOPHOMORE", res.gradeLevel());
        assertEquals("CS", res.intendedMajor());
        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
    }

    @Test
    void getProfile_noProfile_returnsNullProfileFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res = service().getProfile(u);

        assertNull(res.participantType());
        assertNull(res.gradeLevel());
        assertNull(res.intendedMajor());
        assertNull(res.guardianRequired());
        assertNull(res.topicsOfInterest());
        assertNull(res.universitiesOfInterest());
        assertNull(res.accessibilityPreferences());
    }

    @Test
    void getProfile_profileWithNullParticipantType_returnsNullType() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(null); // present profile, but no type set
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res = service().getProfile(u);

        assertNull(res.participantType());
        assertNull(res.gradeLevel());
    }

    // ---- updateProfile: every field set ----

    @Test
    void update_setsAllFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileUpdateRequest r =
                new ParticipantProfileUpdateRequest(
                        "Jordan",
                        "Lee",
                        null,
                        "HIGH_SCHOOL",
                        "JUNIOR",
                        "Biology",
                        List.of("MIT"),
                        List.of("DORM_HOUSING"),
                        "es",
                        "America/New_York",
                        "Wheelchair access");

        ParticipantProfileResponse res = service().updateProfile(u, r);

        assertEquals("Jordan", res.firstName());
        assertEquals("Lee", res.lastName());
        assertEquals("Jordan Lee", res.displayName()); // synced from first/last
        assertEquals("es", res.preferredLanguage());
        assertEquals("America/New_York", res.timezone());
        assertEquals("HIGH_SCHOOL", res.participantType());
        assertEquals("JUNIOR", res.gradeLevel());
        assertEquals("Biology", res.intendedMajor());
        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
        assertEquals(List.of("MIT"), res.universitiesOfInterest());
        assertEquals("Wheelchair access", res.accessibilityPreferences());
    }

    @Test
    void update_allFieldsNull_setsNothingButStillGrantsRole() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        u.setFirstName("Existing");
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileUpdateRequest r =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);

        ParticipantProfileResponse res = service().updateProfile(u, r);

        assertEquals("Existing", res.firstName());
        assertNull(res.displayName()); // no sync triggered, no explicit value
        assertEquals("PROSPECTIVE", res.participantType());
        verify(roleGrant).grant(u, UserRole.PARTICIPANT);
    }

    @Test
    void update_lastNameOnly_syncsDisplayName() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileUpdateRequest r =
                new ParticipantProfileUpdateRequest(
                        null, "Lee", null, null, null, null, null, null, null, null, null);

        ParticipantProfileResponse res = service().updateProfile(u, r);

        assertEquals("Lee", res.lastName());
        assertEquals("Lee", res.displayName());
    }

    @Test
    void update_firstAndLastBlank_displayNameNotSet() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        // Both names provided but whitespace-only → trimmed full name is empty → no displayName.
        ParticipantProfileUpdateRequest r =
                new ParticipantProfileUpdateRequest(
                        "   ", "   ", null, null, null, null, null, null, null, null, null);

        ParticipantProfileResponse res = service().updateProfile(u, r);

        assertNull(res.displayName());
    }

    @Test
    void update_universitiesAndAccessibilityOnly() {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileUpdateRequest r =
                new ParticipantProfileUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("Stanford"),
                        null,
                        null,
                        null,
                        "Sign language");

        ParticipantProfileResponse res = service().updateProfile(user(uid), r);

        assertEquals(List.of("Stanford"), res.universitiesOfInterest());
        assertEquals("Sign language", res.accessibilityPreferences());
    }

    @Test
    void update_existingProfile_path() {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.INTERNATIONAL);
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, List.of("ACADEMICS")));

        // participantType left as the existing one (not re-sent)
        assertEquals("INTERNATIONAL", res.participantType());
        assertEquals(List.of("ACADEMICS"), res.topicsOfInterest());
        verify(profiles).save(existing);
    }

    // ---- readInterests: object form ----

    @Test
    void update_readsObjectFormInterests() {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PROSPECTIVE);
        existing.setInterests("{\"topics\":[\"ATHLETICS\"],\"universities\":[\"UCLA\"]}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, null));

        assertEquals(List.of("ATHLETICS"), res.topicsOfInterest());
        assertEquals(List.of("UCLA"), res.universitiesOfInterest());
    }

    // ---- readInterests: catch block (malformed JSON via mock mapper) ----

    @Test
    void update_malformedInterests_readValueThrows_yieldsEmptyMap() throws Exception {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PROSPECTIVE);
        existing.setInterests("not-json");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ObjectMapper mock = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mock.readValue(org.mockito.ArgumentMatchers.anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("boom"));
        when(mock.writeValueAsString(any())).thenReturn("{}");
        ParticipantService svc =
                new ParticipantService(profiles, users, userRoles, roleGrant, mock);

        ParticipantProfileResponse res =
                svc.updateProfile(user(uid), req(null, null, null, List.of("X")));

        // readValue always throws → readInterests returns empty map (catch). The response is
        // re-read via the mock too, so topics come back as the empty default.
        assertEquals(List.of(), res.topicsOfInterest());
    }

    // ---- writeJson catch block (mock mapper throws on serialize) ----

    @Test
    void update_writeJsonThrows_storesEmptyObject() throws Exception {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ObjectMapper mock = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mock.readValue(org.mockito.ArgumentMatchers.anyString(), any(TypeReference.class)))
                .thenReturn(new java.util.LinkedHashMap<String, Object>());
        when(mock.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        ParticipantService svc =
                new ParticipantService(profiles, users, userRoles, roleGrant, mock);

        svc.updateProfile(user(uid), req(null, null, null, List.of("X")));

        // writeJson swallowed the exception and stored "{}"
        org.mockito.ArgumentCaptor<ParticipantProfileEntity> cap =
                org.mockito.ArgumentCaptor.forClass(ParticipantProfileEntity.class);
        verify(profiles).save(cap.capture());
        assertEquals("{}", cap.getValue().getInterests());
    }

    // ---- readInterests: blank/null inputs ----

    @Test
    void update_blankInterests_treatedAsEmpty() {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PROSPECTIVE);
        existing.setInterests("   "); // blank
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, List.of("Y")));

        assertEquals(List.of("Y"), res.topicsOfInterest());
    }

    @Test
    void update_nullInterests_treatedAsEmpty() {
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PROSPECTIVE);
        existing.setInterests(null);
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), req(null, null, null, List.of("Z")));

        assertEquals(List.of("Z"), res.topicsOfInterest());
    }
}
