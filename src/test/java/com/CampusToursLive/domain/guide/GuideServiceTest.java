package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
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
 * GuideService — guide onboarding (updateProfile) and admin review (reviewApplication). Covers
 * field validation (university, major, price, specialty topics), the draft-vs-submit split, the
 * bidirectional parent/guide exclusion, the GUIDE-role grant on submit, and the approve/reject
 * state machine that makes APPROVED reachable for the live-action gate.
 */
@ExtendWith(MockitoExtension.class)
class GuideServiceTest {

    @Mock GuideProfileRepository guides;
    @Mock GuideVerificationRepository verifications;
    @Mock UniversityRepository universities;
    @Mock ParticipantProfileRepository participants;
    @Mock UserRepository users;
    @Mock RoleGrantService roleGrant;

    private GuideService service() {
        return new GuideService(
                guides,
                verifications,
                universities,
                participants,
                users,
                roleGrant,
                new ObjectMapper());
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileUpdateRequest req(
            String universityId,
            String major,
            List<String> specialties,
            Long basePriceCents,
            String verificationEmail,
            Boolean submit) {
        return new GuideProfileUpdateRequest(
                null,
                null,
                universityId,
                major,
                null,
                null,
                null,
                specialties,
                basePriceCents,
                verificationEmail,
                submit);
    }

    private static RuntimeException badRequest(Runnable r) {
        return assertThrows(RuntimeException.class, r::run);
    }

    // ---- updateProfile: field validation -------------------------------------------------

    @Test
    void update_422_whenUniversityMissing() {
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(UUID.randomUUID()),
                                                req(null, "CS", null, null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
        verifyNoInteractions(roleGrant);
    }

    @Test
    void update_422_whenUniversityIdNotAUuid() {
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(UUID.randomUUID()),
                                                req("not-a-uuid", "CS", null, null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_422_whenUniversityUnknown() {
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(false);
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(UUID.randomUUID()),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        null,
                                                        null,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_422_whenMajorMissing() {
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(UUID.randomUUID()),
                                                req(
                                                        uni.toString(),
                                                        "  ",
                                                        null,
                                                        null,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_422_whenPriceOutOfBounds() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        null,
                                                        100L,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_422_whenSpecialtyTopicInvalid() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        List.of("NOT_A_TOPIC"),
                                                        null,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    // ---- updateProfile: draft vs submit --------------------------------------------------

    @Test
    void update_draft_savesWithoutGrantingGuideRole() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, 5000L, null, false));

        verify(guides).save(any());
        verify(users).save(any());
        verifyNoInteractions(roleGrant, verifications); // draft: no role, no verification record
    }

    @Test
    void update_submit_422_whenVerificationEmailMissing() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(uni.toString(), "CS", null, null, null, true)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_422_whenParentParticipantBecomingGuide() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        ParticipantProfileEntity parent = new ParticipantProfileEntity();
        parent.setParticipantType(ParticipantType.PARENT);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.of(parent));

        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        null,
                                                        null,
                                                        "me@school.edu",
                                                        true)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_grantsGuideRoleAndSetsPendingReview() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileResponse res =
                service()
                        .updateProfile(
                                u, req(uni.toString(), "CS", null, null, "me@school.edu", true));

        assertEquals("PENDING_REVIEW", res.applicationStatus());
        verify(verifications).save(any()); // a UNIVERSITY_EMAIL verification record is created
        verify(roleGrant).grant(u, UserRole.GUIDE);
        verify(users).save(u);
    }

    // ---- reviewApplication ----------------------------------------------------------------

    @Test
    void review_approve_setsApprovedAndVerified() {
        UUID guideUserId = UUID.randomUUID();
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setApplicationStatus(GuideApplicationStatus.PENDING_REVIEW);
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.of(profile));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId)));

        GuideProfileResponse res = service().reviewApplication(guideUserId, "approved");

        assertEquals("APPROVED", res.applicationStatus());
        assertEquals(GuideApplicationStatus.APPROVED, profile.getApplicationStatus());
        assertEquals(GuideVerificationStatus.VERIFIED, profile.getVerificationStatus());
    }

    @Test
    void review_reject_setsRejectedWithoutVerifying() {
        UUID guideUserId = UUID.randomUUID();
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setApplicationStatus(GuideApplicationStatus.PENDING_REVIEW);
        profile.setVerificationStatus(GuideVerificationStatus.PENDING); // submitted, under review
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.of(profile));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId)));

        service().reviewApplication(guideUserId, "REJECTED");

        assertEquals(GuideApplicationStatus.REJECTED, profile.getApplicationStatus());
        // reject must NOT flip the verification to VERIFIED — it stays where it was.
        org.junit.jupiter.api.Assertions.assertEquals(
                GuideVerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void review_422_whenDecisionNotApproveOrReject() {
        var ex = badRequest(() -> service().reviewApplication(UUID.randomUUID(), "MAYBE"));
        assertInstanceOf(ValidationException.class, ex);
        verifyNoInteractions(guides, users);
    }

    @Test
    void review_404_whenNoGuideApplication() {
        UUID guideUserId = UUID.randomUUID();
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.empty());
        var ex = badRequest(() -> service().reviewApplication(guideUserId, "APPROVED"));
        assertInstanceOf(NotFoundException.class, ex);
    }

    @Test
    void review_404_whenUserRowMissing() {
        UUID guideUserId = UUID.randomUUID();
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(UUID.randomUUID());
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.of(profile));
        when(users.findById(guideUserId)).thenReturn(Optional.empty());
        var ex = badRequest(() -> service().reviewApplication(guideUserId, "APPROVED"));
        assertInstanceOf(NotFoundException.class, ex);
    }

    // ---- getProfile -----------------------------------------------------------------------

    @Test
    void getProfile_withExistingProfile_mapsAllFields() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        u.setEmail("g@school.edu");
        u.setAccountStatus(com.CampusToursLive.domain.user.AccountStatus.ACTIVE);
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setUniversityId(uni);
        profile.setMajor("CS");
        profile.setClassYear("2026");
        profile.setBio("hi");
        profile.setLanguages("[\"en-US\"]");
        profile.setSpecialties("[\"GENERAL_CAMPUS\"]");
        profile.setBasePriceCents(5000L);
        profile.setApplicationStatus(GuideApplicationStatus.APPROVED);
        profile.setVerificationStatus(GuideVerificationStatus.VERIFIED);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));

        GuideProfileResponse res = service().getProfile(u);

        assertEquals(uni.toString(), res.universityId());
        assertEquals("CS", res.major());
        assertEquals("APPROVED", res.applicationStatus());
        assertEquals("VERIFIED", res.verificationStatus());
        assertEquals(List.of("en-US"), res.languages());
        assertEquals(List.of("GENERAL_CAMPUS"), res.specialties());
    }

    @Test
    void getProfile_withNoProfile_returnsNullProfileFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileResponse res = service().getProfile(u);

        org.junit.jupiter.api.Assertions.assertNull(res.universityId());
        org.junit.jupiter.api.Assertions.assertNull(res.major());
        org.junit.jupiter.api.Assertions.assertNull(res.applicationStatus());
        org.junit.jupiter.api.Assertions.assertNull(res.verificationStatus());
    }

    @Test
    void getProfile_profilePresentButNullEnumsAndArrays() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        // accountStatus null, applicationStatus null, verificationStatus null, null arrays.
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setApplicationStatus(null);
        profile.setVerificationStatus(null);
        profile.setLanguages(null);
        profile.setSpecialties("   ");
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));

        GuideProfileResponse res = service().getProfile(u);

        org.junit.jupiter.api.Assertions.assertNull(res.accountStatus());
        org.junit.jupiter.api.Assertions.assertNull(res.applicationStatus());
        org.junit.jupiter.api.Assertions.assertNull(res.verificationStatus());
        assertEquals(List.of(), res.languages()); // null json → empty via readArray null branch
        assertEquals(List.of(), res.specialties()); // blank json → empty via readArray blank branch
    }

    // ---- updateProfile: optional fields & display-name sync -------------------------------

    @Test
    void update_lastNameOnly_syncsDisplayNameWithNullFirstName() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid); // firstName stays null → exercises nullToEmpty null branch
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        "Lovelace",
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);

        service().updateProfile(u, r);

        org.junit.jupiter.api.Assertions.assertNull(u.getFirstName());
        assertEquals("Lovelace", u.getLastName());
        assertEquals("Lovelace", u.getDisplayName());
    }

    @Test
    void update_noNames_skipsDisplayNameSync() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        // both names null → L73 condition false (short-circuits) → displayName untouched.
        service().updateProfile(u, req(uni.toString(), "CS", null, null, null, false));

        org.junit.jupiter.api.Assertions.assertNull(u.getDisplayName());
    }

    @Test
    void update_422_whenMajorNull() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        // major null → L83 null branch, L87 "major == null" branch.
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void review_422_whenDecisionNull() {
        // decision null → L201 null branch.
        var ex = badRequest(() -> service().reviewApplication(UUID.randomUUID(), null));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_setsNamesAndSyncsDisplayName() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        "Ada",
                        "Lovelace",
                        uni.toString(),
                        "CS",
                        "2026",
                        "bio",
                        List.of("en-US", "fr-FR"),
                        List.of("GENERAL_CAMPUS"),
                        5000L,
                        null,
                        false);

        service().updateProfile(u, r);

        assertEquals("Ada", u.getFirstName());
        assertEquals("Lovelace", u.getLastName());
        assertEquals("Ada Lovelace", u.getDisplayName());
        verify(guides).save(any());
        verify(users).save(u);
        verifyNoInteractions(roleGrant, verifications);
    }

    @Test
    void update_blankNames_doNotSetDisplayName() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        // firstName/lastName provided but blank → trimmed full name is empty → displayName unset.
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        " ", " ", uni.toString(), "CS", null, null, null, null, null, null, false);

        service().updateProfile(u, r);

        org.junit.jupiter.api.Assertions.assertNull(u.getDisplayName());
    }

    @Test
    void update_languagesAllBlank_fallsBackToDefault() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        java.util.ArrayList<String> langs = new java.util.ArrayList<>();
        langs.add(null);
        langs.add("  ");
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        langs,
                        null,
                        null,
                        null,
                        false);

        GuideProfileResponse res = service().updateProfile(u, r);

        assertEquals(List.of("en-US"), res.languages());
    }

    @Test
    void update_specialtiesWithNullAndBlankEntriesSkipped() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        java.util.ArrayList<String> topics = new java.util.ArrayList<>();
        topics.add(null);
        topics.add("  ");
        topics.add("GENERAL_CAMPUS");
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        topics,
                        null,
                        null,
                        false);

        GuideProfileResponse res = service().updateProfile(u, r);

        assertEquals(List.of("GENERAL_CAMPUS"), res.specialties());
    }

    @Test
    void update_priceTooHigh_422() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        null,
                                                        999999L,
                                                        null,
                                                        false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_universityIdBlank_422Required() {
        UUID uid = UUID.randomUUID();
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req("   ", "CS", null, null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_submitNull_treatedAsDraft() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, null, null, null));

        verify(guides).save(any());
        verifyNoInteractions(roleGrant, verifications);
    }

    @Test
    void update_submit_422_whenEmailHasNoAtSign() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                req(
                                                        uni.toString(),
                                                        "CS",
                                                        null,
                                                        null,
                                                        "noatsign",
                                                        true)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_nonParentParticipant_allowed() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        ParticipantProfileEntity student = new ParticipantProfileEntity();
        student.setParticipantType(ParticipantType.PROSPECTIVE);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.of(student));

        GuideProfileResponse res =
                service()
                        .updateProfile(
                                u, req(uni.toString(), "CS", null, null, "me@school.edu", true));

        assertEquals("PENDING_REVIEW", res.applicationStatus());
        verify(roleGrant).grant(u, UserRole.GUIDE);
    }

    @Test
    void update_existingProfile_isReused() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        GuideProfileEntity existing = new GuideProfileEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(existing));

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, 5000L, null, false));

        verify(guides).save(existing);
    }

    // ---- writeJson / readArray catch blocks (mock ObjectMapper) ---------------------------

    @Test
    void writeJson_catchBlock_returnsEmptyArray() throws Exception {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        ObjectMapper badMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(badMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        GuideService svc =
                new GuideService(
                        guides,
                        verifications,
                        universities,
                        participants,
                        users,
                        roleGrant,
                        badMapper);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        // languages present → writeJson invoked → throws → caught → "[]".
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        List.of("en-US"),
                        null,
                        null,
                        null,
                        false);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> svc.updateProfile(user(uid), r));
    }

    @Test
    void readArray_catchBlock_returnsEmptyList() throws Exception {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        ObjectMapper badMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(badMapper.readValue(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.<TypeReference<List<String>>>any()))
                .thenThrow(new RuntimeException("boom"));
        GuideService svc =
                new GuideService(
                        guides,
                        verifications,
                        universities,
                        participants,
                        users,
                        roleGrant,
                        badMapper);
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setLanguages("[\"en-US\"]"); // non-blank → readValue invoked → throws → []
        profile.setSpecialties("[\"GENERAL_CAMPUS\"]");
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));

        GuideProfileResponse res = svc.getProfile(u);

        assertEquals(List.of(), res.languages());
        assertEquals(List.of(), res.specialties());
    }
}
