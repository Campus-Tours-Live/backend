package com.CampusToursLive.domain.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.OidcIdentity;
import com.CampusToursLive.security.OidcIdentityLock;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    @Mock OidcIdentityLock identityLock;

    private static final OidcIdentity IDENTITY =
            new OidcIdentity("https://accounts.google.com", "sub-1");

    private ParticipantService service() {
        return new ParticipantService(
                profiles, users, userRoles, roleGrant, identityLock, new ObjectMapper());
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
                service().updateProfile(u, IDENTITY, req("Jordan", null, null, null));

        assertEquals("PROSPECTIVE", res.type()); // created default
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
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                IDENTITY,
                                                req(null, null, "BOGUS", null)));
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
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                IDENTITY,
                                                req(null, null, "PARENT", null)));
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
                service().updateProfile(u, IDENTITY, req(null, null, "PARENT", null));

        assertEquals("PARENT", res.type());
        verify(roleGrant).grant(u, UserRole.PARTICIPANT);
    }

    // ---- I14: identity lock on participant_type changes -----------------------------------

    @Test
    void update_typeChangeToParent_acquiresLockBeforeExclusionCheck() {
        // I14: a participant_type change is an eligibility mutation — the identity lock must be
        // acquired FIRST (before the fresh PARENT<->GUIDE exclusion read), so a concurrent guide
        // grant for the SAME identity is serialized against it rather than racing it.
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty()); // default PROSPECTIVE
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);

        service().updateProfile(u, IDENTITY, req(null, null, "PARENT", null));

        InOrder inOrder = Mockito.inOrder(identityLock, userRoles);
        inOrder.verify(identityLock).acquire(IDENTITY);
        inOrder.verify(userRoles).existsByUserIdAndRole(uid, UserRole.GUIDE);
    }

    @Test
    void update_typeChangeToParent_whenGuideHeld_locksThenRejects() {
        // Same race, but the lock hand-off reveals a concurrently-held GUIDE role — I13 must
        // still reject, now proven under the lock rather than racing it.
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .updateProfile(
                                        user(uid), IDENTITY, req(null, null, "PARENT", null)));

        InOrder inOrder = Mockito.inOrder(identityLock, userRoles);
        inOrder.verify(identityLock).acquire(IDENTITY);
        inOrder.verify(userRoles).existsByUserIdAndRole(uid, UserRole.GUIDE);
    }

    @Test
    void update_typeChangeAwayFromParent_alsoAcquiresLock() {
        // I14: the lock must trigger on ANY participant_type change, not only transitions TO
        // PARENT — leaving PARENT also flips PARENT<->GUIDE eligibility.
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PARENT);
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(u, IDENTITY, req(null, null, "TRANSFER", null));

        assertEquals("TRANSFER", res.type());
        verify(identityLock).acquire(IDENTITY);
        // Leaving PARENT is never itself blocked — the exclusion only fires for PARENT.
        verify(userRoles, never()).existsByUserIdAndRole(any(), any());
    }

    @Test
    void update_typeUnchanged_doesNotAcquireLock() {
        // No participant_type change requested → not an eligibility mutation → no lock needed.
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.INTERNATIONAL);
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        service().updateProfile(user(uid), IDENTITY, req(null, null, null, null));

        verifyNoInteractions(identityLock);
    }

    @Test
    void update_typeResentSameValue_doesNotAcquireLock() {
        // Re-sending the SAME participantType is not a "change" — no new eligibility mutation.
        UUID uid = UUID.randomUUID();
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.PARENT);
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);

        service().updateProfile(user(uid), IDENTITY, req(null, null, "PARENT", null));

        verifyNoInteractions(identityLock);
    }

    @Test
    void update_storesTopicsOfInterest() {
        UUID uid = UUID.randomUUID();
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res =
                service()
                        .updateProfile(
                                user(uid),
                                IDENTITY,
                                req(null, null, null, List.of("DORM_HOUSING")));

        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
    }

    @Test
    void update_422_whenFirstNameHasInvalidCharacters() {
        UUID uid = UUID.randomUUID();
        // A digit in the name → NameRules rejects (server-side defense; the client also blocks it).
        var ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                IDENTITY,
                                                req("Ann3", null, null, null)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_explicitDisplayNameWinsOverFirstLastSync() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        service().updateProfile(u, IDENTITY, req("Jordan", "JL the Guide", null, null));

        assertEquals("JL the Guide", u.getDisplayName());
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
                service().updateProfile(user(uid), IDENTITY, req(null, null, null, null));

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

        assertEquals("TRANSFER", res.type());
        assertEquals("SOPHOMORE", res.gradeLevel());
        assertEquals("CS", res.intendedMajor());
        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
        assertEquals("VERIFIED", res.participantStatus()); // guardianRequired defaults false
    }

    @Test
    void getProfile_noProfile_returnsNullProfileFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(profiles.findByUserId(uid)).thenReturn(Optional.empty());

        ParticipantProfileResponse res = service().getProfile(u);

        assertNull(res.participantStatus());
        assertNull(res.type());
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

        assertNull(res.type());
        assertNull(res.gradeLevel());
    }

    @Test
    void getProfile_guardianRequired_returnsPendingParticipantStatus() {
        // Underage-participant path: guardianRequired=true → participantStatus="PENDING". The
        // real guardian-verification flow is future work (Phase 4); this just covers the branch.
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        ParticipantProfileEntity existing = new ParticipantProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        existing.setParticipantType(ParticipantType.HIGH_SCHOOL);
        existing.setGuardianRequired(true);
        existing.setInterests("{}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res = service().getProfile(u);

        assertEquals("PENDING", res.participantStatus());
    }

    @Test
    void getProfile_doesNotExposeIdentityFields() {
        // Regression guard for the profile-contract-v2 identity split: /participant/profile is
        // flat and role-scoped — identity (user id, name, email, account status) lives only on
        // /userinfo. ParticipantProfileResponse no longer declares those accessors at all, so
        // this test documents the removal (would fail to compile if they came back).
        List<String> fieldNames =
                java.util.Arrays.stream(ParticipantProfileResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("userId"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("firstName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("lastName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("displayName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("email"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("participantType"));
        org.junit.jupiter.api.Assertions.assertTrue(fieldNames.contains("type"));
        org.junit.jupiter.api.Assertions.assertTrue(fieldNames.contains("participantStatus"));
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
                        List.of("166683"), // College Scorecard school id (MIT)
                        List.of("DORM_HOUSING"),
                        "es",
                        "America/New_York",
                        "Wheelchair access");

        ParticipantProfileResponse res = service().updateProfile(u, IDENTITY, r);

        assertEquals("Jordan", u.getFirstName());
        assertEquals("Lee", u.getLastName());
        assertEquals("Jordan Lee", u.getDisplayName()); // synced from first/last
        assertEquals("es", res.preferredLanguage());
        assertEquals("America/New_York", res.timezone());
        assertEquals("HIGH_SCHOOL", res.type());
        assertEquals("JUNIOR", res.gradeLevel());
        assertEquals("Biology", res.intendedMajor());
        assertEquals(List.of("DORM_HOUSING"), res.topicsOfInterest());
        assertEquals(List.of("166683"), res.universitiesOfInterest());
        assertEquals("Wheelchair access", res.accessibilityPreferences());
        assertEquals("VERIFIED", res.participantStatus());
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

        ParticipantProfileResponse res = service().updateProfile(u, IDENTITY, r);

        assertEquals("Existing", u.getFirstName());
        assertNull(u.getDisplayName()); // no sync triggered, no explicit value
        assertEquals("PROSPECTIVE", res.type());
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

        service().updateProfile(u, IDENTITY, r);

        assertEquals("Lee", u.getLastName());
        assertEquals("Lee", u.getDisplayName());
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

        service().updateProfile(u, IDENTITY, r);

        assertNull(u.getDisplayName());
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
                        List.of("243744"), // College Scorecard school id (Stanford)
                        null,
                        null,
                        null,
                        "Sign language");

        ParticipantProfileResponse res = service().updateProfile(user(uid), IDENTITY, r);

        assertEquals(List.of("243744"), res.universitiesOfInterest());
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
                service()
                        .updateProfile(
                                user(uid), IDENTITY, req(null, null, null, List.of("ACADEMICS")));

        // participantType left as the existing one (not re-sent)
        assertEquals("INTERNATIONAL", res.type());
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
        // universities: College Scorecard school id (UCLA)
        existing.setInterests("{\"topics\":[\"ATHLETICS\"],\"universities\":[\"110662\"]}");
        when(profiles.findByUserId(uid)).thenReturn(Optional.of(existing));

        ParticipantProfileResponse res =
                service().updateProfile(user(uid), IDENTITY, req(null, null, null, null));

        assertEquals(List.of("ATHLETICS"), res.topicsOfInterest());
        assertEquals(List.of("110662"), res.universitiesOfInterest());
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
                new ParticipantService(profiles, users, userRoles, roleGrant, identityLock, mock);

        ParticipantProfileResponse res =
                svc.updateProfile(user(uid), IDENTITY, req(null, null, null, List.of("X")));

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
                new ParticipantService(profiles, users, userRoles, roleGrant, identityLock, mock);

        svc.updateProfile(user(uid), IDENTITY, req(null, null, null, List.of("X")));

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
                service().updateProfile(user(uid), IDENTITY, req(null, null, null, List.of("Y")));

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
                service().updateProfile(user(uid), IDENTITY, req(null, null, null, List.of("Z")));

        assertEquals(List.of("Z"), res.topicsOfInterest());
    }
}
