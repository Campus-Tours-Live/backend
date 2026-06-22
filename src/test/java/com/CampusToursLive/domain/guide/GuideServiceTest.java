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
}
