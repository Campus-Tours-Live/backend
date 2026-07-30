package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.university.CampusImageUrls;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.security.GuideProfileSnapshot;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import com.CampusToursLive.web.dto.GuideUniversityView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideService — guide onboarding (updateProfile). Covers field validation (university, major,
 * price, tour topics), the draft-vs-submit split, the bidirectional parent/guide exclusion, and the
 * GUIDE-role grant on submit. guideStatus is verification-driven — VERIFIED is set outside this
 * service (the stubbed email-verify flow), so it's exercised here only via getProfile mapping the
 * stored enum through to the response.
 */
@ExtendWith(MockitoExtension.class)
class GuideServiceTest {

    @Mock GuideProfileRepository guides;
    @Mock GuideUniversityRepository guideUniversities;
    @Mock UniversityRepository universities;
    @Mock ParticipantProfileRepository participants;
    @Mock UserRepository users;
    @Mock RoleGrantService roleGrant;
    @Mock SchoolDirectory schools;
    private final CampusImageUrls campusImages = new CampusImageUrls("https://r2.example/");

    /**
     * Fixed at a single instant so the entryYear/classYear window tests are true regardless of what
     * day this suite happens to run — see EnrollmentYearRulesTest for the same clock.
     */
    private static final Clock TEST_CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    private final EnrollmentYearRules rules = new EnrollmentYearRules(TEST_CLOCK);

    /** A stable, always-valid university id for the entryYear/classYear tests below. */
    private static final String UNIVERSITY_ID = UUID.randomUUID().toString();

    private GuideService service() {
        return new GuideService(
                guides,
                guideUniversities,
                universities,
                participants,
                users,
                roleGrant,
                schools,
                campusImages,
                new ObjectMapper(),
                rules);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileUpdateRequest req(
            String universityId,
            String major,
            List<String> tourTopics,
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
                tourTopics,
                verificationEmail,
                submit,
                "Bachelor's Degree",
                null);
    }

    /**
     * A complete submit=true request — includes the now-required bio + tourTopics (and a valid
     * verification email) so submit reaches finalization instead of tripping a required-field
     * guard.
     */
    private static GuideProfileUpdateRequest submitReq(
            String universityId, String bio, List<String> tourTopics) {
        return new GuideProfileUpdateRequest(
                null,
                null,
                universityId,
                "CS",
                null,
                bio,
                null,
                tourTopics,
                "me@school.edu",
                true,
                "Bachelor's Degree",
                null);
    }

    private static RuntimeException badRequest(Runnable r) {
        return assertThrows(RuntimeException.class, r::run);
    }

    /** Defaults to a bachelor's degree — the common case for these tests. */
    private static GuideProfileUpdateRequest guideRequestWith(Integer entryYear, String classYear) {
        return guideRequestWith(entryYear, classYear, "Bachelor's Degree");
    }

    /**
     * A complete, otherwise-valid guide profile request varying only the three inputs to the year
     * rule. `degree` is one of them: it sets classYear's ceiling, so it belongs here rather than
     * being fixed filler.
     */
    private static GuideProfileUpdateRequest guideRequestWith(
            Integer entryYear, String classYear, String degree) {
        return new GuideProfileUpdateRequest(
                "Maya",
                "Chen",
                UNIVERSITY_ID,
                "Marine Biology",
                classYear,
                "Third-year student and campus tour lead.",
                List.of("en-US"),
                List.of("DORM_HOUSING"),
                "maya.chen@ncu.edu",
                false,
                degree,
                entryYear);
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
                                                req(null, "CS", null, null, false)));
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
                                                req("not-a-uuid", "CS", null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_upsertsLiveDirectorySchool_whenUniversityIdIsNotLocalUuid() {
        UUID uid = UUID.randomUUID();
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(universities.findBySlug("sc-243744")).thenReturn(Optional.empty());
        when(schools.getSchool("243744"))
                .thenReturn(
                        new SchoolDirectory.SchoolRef(
                                "243744", "Stanford University", "Stanford", "Stanford", "CA"));
        when(universities.save(org.mockito.ArgumentMatchers.any(UniversityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service().updateProfile(user(uid), req("243744", "CS", null, null, false)));

        ArgumentCaptor<UniversityEntity> saved = ArgumentCaptor.forClass(UniversityEntity.class);
        verify(universities).save(saved.capture());
        assertEquals("sc-243744", saved.getValue().getSlug());
        assertEquals("Stanford University", saved.getValue().getName());
        assertEquals("Stanford", saved.getValue().getShortName());
        assertEquals("America/Los_Angeles", saved.getValue().getTimezone()); // CA → Pacific
    }

    @Test
    void upsertFromDirectory_setsDerivedImageUrl() {
        when(universities.findBySlug("sc-166027")).thenReturn(Optional.empty());
        when(schools.getSchool("166027"))
                .thenReturn(
                        new SchoolDirectory.SchoolRef(
                                "166027", "Harvard University", "Harvard", "Cambridge", "MA"));
        ArgumentCaptor<UniversityEntity> saved = ArgumentCaptor.forClass(UniversityEntity.class);
        when(universities.save(saved.capture())).thenAnswer(i -> i.getArgument(0));

        service().resolveUniversityForTest("166027");

        // BOTH image_url write paths must produce the same URL for the same campus.
        // This is the CREATE path (a university first seen via Scorecard). The BACKFILL path is
        // TourOfferingServiceTest#backfillsCampusImageOnFirstOffering (search:
        // Harvard%20University),
        // which asserts this identical value. They agree because both go through
        // CampusImageUrls#forName with that university's name -- keep the two expectations in step.
        assertEquals("https://r2.example/Harvard%20University.png", saved.getValue().getImageUrl());
    }

    @Test
    void upsertFromDirectory_absorbsExistingUniversityByName_reKeyingSlugAndShortName() {
        // A legacy seed row ("foo" slug, null shortName) shares the Scorecard school's name.
        // Absorption must REUSE that row (re-key its slug into the sc-<id> namespace and fill in
        // the Scorecard shortName) instead of inserting a brand-new sc-X row.
        UUID existingId = UUID.randomUUID();
        UniversityEntity existing = new UniversityEntity();
        existing.setId(existingId);
        existing.setSlug("foo");
        existing.setName("University of Foo");
        existing.setShortName(null);
        existing.setCity("Foo City");
        existing.setRegion("CA");
        existing.setTimezone("America/Los_Angeles");
        existing.setStatus(UniversityStatus.ACTIVE);
        existing.setImageUrl("https://existing.example/foo.png");

        when(universities.findBySlug("sc-X")).thenReturn(Optional.empty());
        when(schools.getSchool("X"))
                .thenReturn(
                        new SchoolDirectory.SchoolRef(
                                "X", "University of Foo", "Foo", "Foo City", "CA"));
        when(universities.findFirstByName("University of Foo")).thenReturn(Optional.of(existing));
        when(universities.save(org.mockito.ArgumentMatchers.any(UniversityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UUID result = service().resolveUniversityForTest("X");

        assertEquals(existingId, result);
        assertEquals("sc-X", existing.getSlug());
        assertEquals("Foo", existing.getShortName());
        // Absorption must not overwrite the existing row's city/region/imageUrl.
        assertEquals("Foo City", existing.getCity());
        assertEquals("CA", existing.getRegion());
        assertEquals("https://existing.example/foo.png", existing.getImageUrl());
        verify(universities).save(existing);
    }

    @Test
    void upsertFromDirectory_absorb_doesNotWipeExistingShortName_whenScorecardAliasIsNull() {
        UUID existingId = UUID.randomUUID();
        UniversityEntity existing = new UniversityEntity();
        existing.setId(existingId);
        existing.setSlug("foo");
        existing.setName("University of Foo");
        existing.setShortName("Legacy");
        existing.setCity("Foo City");
        existing.setRegion("CA");
        existing.setTimezone("America/Los_Angeles");
        existing.setStatus(UniversityStatus.ACTIVE);

        when(universities.findBySlug("sc-X")).thenReturn(Optional.empty());
        when(schools.getSchool("X"))
                .thenReturn(
                        new SchoolDirectory.SchoolRef(
                                "X", "University of Foo", null, "Foo City", "CA"));
        when(universities.findFirstByName("University of Foo")).thenReturn(Optional.of(existing));
        when(universities.save(org.mockito.ArgumentMatchers.any(UniversityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UUID result = service().resolveUniversityForTest("X");

        assertEquals(existingId, result);
        assertEquals("sc-X", existing.getSlug());
        assertEquals("Legacy", existing.getShortName()); // not wiped by a null Scorecard alias
    }

    @Test
    void upsertFromDirectory_noNameMatch_createsNewRow() {
        when(universities.findBySlug("sc-Y")).thenReturn(Optional.empty());
        when(schools.getSchool("Y"))
                .thenReturn(
                        new SchoolDirectory.SchoolRef(
                                "Y", "Brand New University", "BNU", "Nowhere", "TX"));
        when(universities.findFirstByName("Brand New University")).thenReturn(Optional.empty());
        ArgumentCaptor<UniversityEntity> saved = ArgumentCaptor.forClass(UniversityEntity.class);
        when(universities.save(saved.capture())).thenAnswer(i -> i.getArgument(0));

        service().resolveUniversityForTest("Y");

        assertEquals("sc-Y", saved.getValue().getSlug());
        assertEquals("Brand New University", saved.getValue().getName());
        assertEquals("BNU", saved.getValue().getShortName());
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
                                                req(uni.toString(), "CS", null, null, false)));
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
                                                req(uni.toString(), "  ", null, null, false)));
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

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, null, false));

        verify(guides).save(any());
        verify(users).save(any());
        verifyNoInteractions(roleGrant); // draft: no role granted
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
                                                req(uni.toString(), "CS", null, null, true)));
        assertInstanceOf(ValidationException.class, ex);
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_409_roleNotEligible_whenParentParticipantBecomingGuide() {
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
                                                        "me@school.edu",
                                                        true)));
        assertInstanceOf(ConflictException.class, ex);
        ConflictException cex = (ConflictException) ex;
        assertEquals("ROLE_NOT_ELIGIBLE", cex.code());
        assertEquals("GUIDE", cex.properties().get("role"));
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_grantsGuideRoleAndSetsPending() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileResponse res =
                service()
                        .updateProfile(
                                u,
                                submitReq(
                                        uni.toString(),
                                        "I lead weekly campus tours for prospective students.",
                                        List.of("GENERAL_CAMPUS")));

        assertEquals("PENDING", res.guideStatus());
        verify(roleGrant).grant(u, UserRole.GUIDE);
        verify(users).save(u);
    }

    @Test
    void update_submit_upsertsGuideUniversityRow_withSchoolEmailAndPendingStatus() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        service()
                .updateProfile(
                        u,
                        submitReq(
                                uni.toString(),
                                "I lead weekly campus tours for prospective students.",
                                List.of("GENERAL_CAMPUS")));

        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        GuideUniversityEntity saved = captor.getValue();
        assertEquals(uni, saved.getUniversityId());
        assertEquals("CS", saved.getMajor());
        assertEquals("Bachelor's Degree", saved.getDegree());
        assertEquals("me@school.edu", saved.getSchoolEmail());
        assertEquals(GuideVerificationStatus.PENDING, saved.getVerificationStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getGuideProfileId());
    }

    @Test
    void update_submit_updatesExistingGuideUniversityRow_matchingUniversityId() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity u = user(uid);
        GuideProfileEntity existing = new GuideProfileEntity();
        existing.setId(profileId);
        existing.setUserId(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(existing));
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        UUID rowId = UUID.randomUUID();
        GuideUniversityEntity existingRow = new GuideUniversityEntity();
        existingRow.setId(rowId);
        existingRow.setGuideProfileId(profileId);
        existingRow.setUniversityId(uni);
        existingRow.setMajor("Old Major");
        when(guideUniversities.findByGuideProfileId(profileId)).thenReturn(List.of(existingRow));

        service()
                .updateProfile(
                        u,
                        submitReq(
                                uni.toString(),
                                "I lead weekly campus tours for prospective students.",
                                List.of("GENERAL_CAMPUS")));

        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        GuideUniversityEntity saved = captor.getValue();
        assertEquals(rowId, saved.getId()); // reused the existing row, not a new insert
        assertEquals("CS", saved.getMajor());
        assertEquals("me@school.edu", saved.getSchoolEmail());
        assertEquals(GuideVerificationStatus.PENDING, saved.getVerificationStatus());
    }

    @Test
    void update_draft_upsertsGuideUniversityRow_withoutSchoolEmail() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, null, false));

        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        GuideUniversityEntity saved = captor.getValue();
        assertEquals(uni, saved.getUniversityId());
        assertEquals("CS", saved.getMajor());
        org.junit.jupiter.api.Assertions.assertNull(saved.getSchoolEmail());
        assertEquals(GuideVerificationStatus.NOT_SUBMITTED, saved.getVerificationStatus());
    }

    @Test
    void getProfile_doesNotExposeSchoolEmail() {
        // Regression guard: schoolEmail (PII) lives only on guide_universities and must never
        // leak through GuideProfileResponse or GuideUniversityView.
        List<String> responseFields =
                java.util.Arrays.stream(GuideProfileResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();
        org.junit.jupiter.api.Assertions.assertFalse(responseFields.contains("schoolEmail"));
        List<String> universityViewFields =
                java.util.Arrays.stream(GuideUniversityView.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();
        org.junit.jupiter.api.Assertions.assertFalse(universityViewFields.contains("schoolEmail"));
    }

    @Test
    void getProfile_flatUniversityFieldsAreGone_replacedByUniversitiesArray() {
        // Regression guard for this task: the Phase-1 flat universityId/major/classYear/degree/
        // verificationStatus fields are removed from GuideProfileResponse — replaced by
        // universities[]. Would fail to compile if they came back.
        List<String> fieldNames =
                java.util.Arrays.stream(GuideProfileResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("universityId"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("universityName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("universityShortName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("major"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("classYear"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("degree"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("verificationStatus"));
        org.junit.jupiter.api.Assertions.assertTrue(fieldNames.contains("universities"));
    }

    @Test
    void update_submit_422_whenBioMissing() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        // null bio (the == null branch) and a blank bio (the isBlank branch) are both rejected.
        var nullBio =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                submitReq(
                                                        uni.toString(),
                                                        null,
                                                        List.of("GENERAL_CAMPUS"))));
        assertInstanceOf(ValidationException.class, nullBio);
        assertTrue(nullBio.getMessage().contains("bio"));

        var blankBio =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                submitReq(
                                                        uni.toString(),
                                                        "   ",
                                                        List.of("GENERAL_CAMPUS"))));
        assertInstanceOf(ValidationException.class, blankBio);
        assertTrue(blankBio.getMessage().contains("bio"));
        verify(roleGrant, never()).grant(any(), any());
    }

    @Test
    void update_submit_422_whenSpecialtiesMissing() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(participants.findByUserId(uid)).thenReturn(Optional.empty());

        // Bio present but no specialties → the specialties guard trips.
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid),
                                                submitReq(
                                                        uni.toString(),
                                                        "I lead weekly campus tours.",
                                                        List.of())));
        assertInstanceOf(ValidationException.class, ex);
        assertTrue(ex.getMessage().toLowerCase().contains("specialty"));
        verify(roleGrant, never()).grant(any(), any());
    }

    // ---- getProfile -----------------------------------------------------------------------

    @Test
    void getProfile_withExistingProfile_mapsAllFields() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity u = user(uid);
        u.setEmail("g@school.edu");
        u.setAccountStatus(com.CampusToursLive.domain.user.AccountStatus.ACTIVE);
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(profileId);
        profile.setBio("hi");
        profile.setSpokenLanguages("[\"en-US\"]");
        profile.setTourTopics("[\"GENERAL_CAMPUS\"]");
        profile.setStatus(GuideStatus.VERIFIED);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));
        UniversityEntity university = new UniversityEntity();
        university.setId(uni);
        university.setName("Stanford University");
        university.setShortName("Stanford");
        when(universities.findById(uni)).thenReturn(Optional.of(university));

        GuideUniversityEntity row = new GuideUniversityEntity();
        row.setId(UUID.randomUUID());
        row.setGuideProfileId(profileId);
        row.setUniversityId(uni);
        row.setMajor("CS");
        row.setDegree("Bachelor's Degree");
        row.setClassYear("2026");
        row.setEntryYear(2022);
        row.setVerificationStatus(GuideVerificationStatus.VERIFIED);
        when(guideUniversities.findByGuideProfileId(profileId)).thenReturn(List.of(row));

        GuideProfileResponse res = service().getProfile(u);

        assertEquals("VERIFIED", res.guideStatus());
        assertEquals(List.of("en-US"), res.spokenLanguages());
        assertEquals(List.of("GENERAL_CAMPUS"), res.tourTopics());
        assertEquals(1, res.universities().size());
        GuideUniversityView view = res.universities().get(0);
        assertEquals(uni.toString(), view.universityId());
        assertEquals("Stanford University", view.universityName());
        assertEquals("Stanford", view.universityShortName());
        assertEquals("CS", view.major());
        assertEquals("Bachelor's Degree", view.degree());
        assertEquals("2026", view.classYear());
        assertEquals(2022, view.entryYear());
        assertEquals("VERIFIED", view.verificationStatus());
    }

    @Test
    void getProfile_withNoProfile_returnsNullProfileFields() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        GuideProfileResponse res = service().getProfile(u);

        org.junit.jupiter.api.Assertions.assertNull(res.guideStatus());
        org.junit.jupiter.api.Assertions.assertTrue(res.universities().isEmpty());
    }

    @Test
    void getProfile_profilePresentButNullEnumsAndArrays() {
        UUID uid = UUID.randomUUID();
        UserEntity u = user(uid);
        // guideStatus null, null arrays.
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setStatus(null);
        profile.setSpokenLanguages(null);
        profile.setTourTopics("   ");
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));

        GuideProfileResponse res = service().getProfile(u);

        org.junit.jupiter.api.Assertions.assertNull(res.guideStatus());
        org.junit.jupiter.api.Assertions.assertTrue(res.universities().isEmpty());
        assertEquals(
                List.of(), res.spokenLanguages()); // null json → empty via readArray null branch
        assertEquals(List.of(), res.tourTopics()); // blank json → empty via readArray blank branch
    }

    @Test
    void getProfile_doesNotExposeIdentityFields() {
        // Regression guard for the profile-contract-v2 identity split: /guide/profile is
        // role-scoped — identity (user id, name, email, account status) lives only
        // on /userinfo. GuideProfileResponse no longer declares those accessors at all, so
        // this test documents the removal (would fail to compile if they came back).
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity u = user(uid);
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(profileId);
        profile.setStatus(GuideStatus.PENDING);
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));
        when(universities.findById(uni)).thenReturn(Optional.empty());
        GuideUniversityEntity row = new GuideUniversityEntity();
        row.setId(UUID.randomUUID());
        row.setGuideProfileId(profileId);
        row.setUniversityId(uni);
        row.setMajor("CS");
        when(guideUniversities.findByGuideProfileId(profileId)).thenReturn(List.of(row));

        GuideProfileResponse res = service().getProfile(u);

        assertEquals("PENDING", res.guideStatus());
        assertEquals(uni.toString(), res.universities().get(0).universityId());
        List<String> fieldNames =
                java.util.Arrays.stream(GuideProfileResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("userId"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("firstName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("lastName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("displayName"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("email"));
        org.junit.jupiter.api.Assertions.assertFalse(fieldNames.contains("accountStatus"));
    }

    // ---- getProfile(GuideProfileSnapshot): the GET /guide/profile read path (CTL-97 Task 5) ---

    @Test
    void getProfile_fromSnapshot_mapsAllFieldsWithoutRequeryingGuideProfiles() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        GuideProfileSnapshot snapshot =
                new GuideProfileSnapshot(
                        profileId,
                        uid,
                        "hi",
                        "[\"en-US\"]",
                        "[\"GENERAL_CAMPUS\"]",
                        GuideStatus.VERIFIED,
                        Instant.now(),
                        Instant.now());
        UniversityEntity university = new UniversityEntity();
        university.setId(uni);
        university.setName("Stanford University");
        university.setShortName("Stanford");
        when(universities.findById(uni)).thenReturn(Optional.of(university));
        GuideUniversityEntity row = new GuideUniversityEntity();
        row.setId(UUID.randomUUID());
        row.setGuideProfileId(profileId);
        row.setUniversityId(uni);
        row.setMajor("CS");
        row.setDegree("Bachelor's Degree");
        row.setClassYear("2026");
        row.setEntryYear(2022);
        row.setVerificationStatus(GuideVerificationStatus.VERIFIED);
        when(guideUniversities.findByGuideProfileId(profileId)).thenReturn(List.of(row));

        GuideProfileResponse res = service().getProfile(snapshot);

        assertEquals("VERIFIED", res.guideStatus());
        assertEquals("hi", res.bio());
        assertEquals(List.of("en-US"), res.spokenLanguages());
        assertEquals(List.of("GENERAL_CAMPUS"), res.tourTopics());
        assertEquals(1, res.universities().size());
        assertEquals(uni.toString(), res.universities().get(0).universityId());
        verify(guides, never()).findByUserId(any());
    }

    @Test
    void getProfile_fromSnapshot_nullStatusAndBlankArrays_mapToNullAndEmpty() {
        GuideProfileSnapshot snapshot =
                new GuideProfileSnapshot(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        "   ",
                        null,
                        Instant.now(),
                        Instant.now());
        when(guideUniversities.findByGuideProfileId(snapshot.id())).thenReturn(List.of());

        GuideProfileResponse res = service().getProfile(snapshot);

        org.junit.jupiter.api.Assertions.assertNull(res.guideStatus());
        org.junit.jupiter.api.Assertions.assertNull(res.bio());
        assertEquals(List.of(), res.spokenLanguages());
        assertEquals(List.of(), res.tourTopics());
        org.junit.jupiter.api.Assertions.assertTrue(res.universities().isEmpty());
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
                        false,
                        "Bachelor's Degree",
                        null);

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
        service().updateProfile(u, req(uni.toString(), "CS", null, null, false));

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
                                                req(uni.toString(), null, null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_setsNamesAndSyncsDisplayName() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        // classYear is now anchored on entryYear (not on today), so an entryYear that puts 2026
        // inside the Bachelor's window [entryYear+1, entryYear+6] must be supplied.
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
                        null,
                        false,
                        "Bachelor's Degree",
                        2023);

        service().updateProfile(u, r);

        assertEquals("Ada", u.getFirstName());
        assertEquals("Lovelace", u.getLastName());
        assertEquals("Ada Lovelace", u.getDisplayName());
        verify(guides).save(any());
        verify(users).save(u);
        verifyNoInteractions(roleGrant);
    }

    @Test
    void update_persistsDegreeAndReturnsIt() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        null);

        service().updateProfile(u, r);

        // Degree now lives on the guide_universities row, not a flat response field.
        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        assertEquals("Bachelor's Degree", captor.getValue().getDegree());
    }

    @Test
    void update_persistsEntryYearOnGuideUniversityRow() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        2021);

        service().updateProfile(u, r);

        // entryYear now lives on the guide_universities row, not a flat response field.
        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        assertEquals(2021, captor.getValue().getEntryYear());
    }

    @Test
    void update_422_whenDegreeMissing() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        // Valid university + major, but degree null → degree-required branch.
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null);
        var ex = badRequest(() -> service().updateProfile(user(uid), r));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_422_whenClassYearNotFourDigits() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        "20",
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        null);
        var ex = badRequest(() -> service().updateProfile(user(uid), r));
        assertInstanceOf(ValidationException.class, ex);
    }

    /**
     * classYear is anchored on entryYear now, not on today (see EnrollmentYearRules), so a
     * "too-far-in-the-future" classYear must be expressed relative to a supplied entryYear rather
     * than relative to the current year.
     */
    @Test
    void update_422_whenClassYearOutOfRange() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        int entryYear = 2023;
        String tooFarFuture =
                String.valueOf(rules.classYearRange(entryYear, "Bachelor's Degree").max() + 1);
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        tooFarFuture,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        entryYear);
        var ex = badRequest(() -> service().updateProfile(user(uid), r));
        assertInstanceOf(ValidationException.class, ex);
    }

    /** classYear within [entryYear+1, entryYear+6] (Bachelor's) is accepted. */
    @Test
    void update_acceptsValidClassYearWithinDegreeWindow() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        UserEntity u = user(uid);
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());
        int entryYear = 2023;
        String year = String.valueOf(rules.classYearRange(entryYear, "Bachelor's Degree").min());
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        year,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        entryYear);

        service().updateProfile(u, r);

        // classYear now lives on the guide_universities row, not a flat response field.
        ArgumentCaptor<GuideUniversityEntity> captor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities).save(captor.capture());
        assertEquals(year, captor.getValue().getClassYear());
    }

    /**
     * Below entryYear+1 is rejected — graduating in or before your own enrolment year is not a real
     * case (see EnrollmentYearRules#classYearRange).
     */
    @Test
    void update_422_whenClassYearBelowFloor() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        int entryYear = 2023;
        String belowFloor =
                String.valueOf(rules.classYearRange(entryYear, "Bachelor's Degree").min() - 1);
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        belowFloor,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        entryYear);
        var ex = badRequest(() -> service().updateProfile(user(uid), r));
        assertInstanceOf(ValidationException.class, ex);
    }

    /**
     * A blank classYear used to be silently accepted and stored as an empty string. It is now
     * rejected outright — " " is neither "leave alone" (null) nor a year.
     */
    @Test
    void update_blankClassYearIsRejected() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        null,
                        null,
                        uni.toString(),
                        "CS",
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        null);
        var ex = badRequest(() -> service().updateProfile(user(uid), r));
        assertInstanceOf(ValidationException.class, ex);
    }

    // ---- updateProfile: entryYear range + classYear anchored on enrolment (CTL-97 Task 2) -----

    @Test
    void updateProfile_rejectsEntryYearBelowTheFloor() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);

        GuideProfileUpdateRequest req =
                guideRequestWith(/* entryYear */ 2015, /* classYear */ null);
        ValidationException ex =
                assertThrows(
                        ValidationException.class, () -> service().updateProfile(user(uid), req));
        assertTrue(ex.getMessage().contains("entryYear"));
    }

    @Test
    void updateProfile_rejectsEntryYearAboveTheCeiling() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);

        GuideProfileUpdateRequest req = guideRequestWith(2028, null);
        assertThrows(ValidationException.class, () -> service().updateProfile(user(uid), req));
    }

    @Test
    void updateProfile_acceptsEntryYearExactlyAtBothBounds() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        service().updateProfile(user(uid), guideRequestWith(2016, null));
        service().updateProfile(user(uid), guideRequestWith(2027, null));
    }

    @Test
    void updateProfile_classYearIsAnchoredOnEntryYearNotToday() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        when(guideUniversities.findByGuideProfileId(any())).thenReturn(List.of());

        // entry 2023 + bachelor(6) → [2024, 2029].
        service().updateProfile(user(uid), guideRequestWith(2023, "2024"));
        service().updateProfile(user(uid), guideRequestWith(2023, "2029"));
        assertThrows(
                ValidationException.class,
                () -> service().updateProfile(user(uid), guideRequestWith(2023, "2023")));
        assertThrows(
                ValidationException.class,
                () -> service().updateProfile(user(uid), guideRequestWith(2023, "2030")));
    }

    /**
     * Under the OLD current-year anchoring this passed: 2016 sits inside [currentYear-10,
     * currentYear+6]. Anchored on enrolment it cannot — a 2025 enrollee did not graduate in 2016.
     * This is the defect the re-anchoring exists to close.
     */
    @Test
    void updateProfile_rejectsAGraduationYearBeforeEnrolment() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().updateProfile(user(uid), guideRequestWith(2025, "2016")));
    }

    @Test
    void updateProfile_stillRejectsANonFourDigitClassYear() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().updateProfile(user(uid), guideRequestWith(2023, "24")));
    }

    /**
     * A blank classYear is neither "leave alone" nor a year. Accepting it silently stored an empty
     * string in class_year — a third state the rest of the system does not model.
     */
    @Test
    void updateProfile_rejectsABlankClassYearRatherThanStoringAnEmptyString() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().updateProfile(user(uid), guideRequestWith(2023, "   ")));
    }

    // ---- updateProfile: PATCH validates the merged pair, not just the sent half (CTL-97 Task 4)

    /**
     * Captures the entities the immediately-preceding {@code updateProfile} call persisted, and
     * wires the mocks so the NEXT call sees them as the already-stored row — simulating a real
     * PATCH against previously-saved state without a real database.
     */
    private GuideUniversityEntity stubStoredState(UUID uid) {
        ArgumentCaptor<GuideProfileEntity> profileCaptor =
                ArgumentCaptor.forClass(GuideProfileEntity.class);
        verify(guides, org.mockito.Mockito.atLeastOnce()).save(profileCaptor.capture());
        GuideProfileEntity storedProfile = profileCaptor.getValue();

        ArgumentCaptor<GuideUniversityEntity> rowCaptor =
                ArgumentCaptor.forClass(GuideUniversityEntity.class);
        verify(guideUniversities, org.mockito.Mockito.atLeastOnce()).save(rowCaptor.capture());
        GuideUniversityEntity storedRow = rowCaptor.getValue();

        when(guides.findByUserId(uid)).thenReturn(Optional.of(storedProfile));
        when(guideUniversities.findByGuideProfileId(storedProfile.getId()))
                .thenReturn(List.of(storedRow));
        return storedRow;
    }

    @Test
    void updateProfile_changingOnlyEntryYear_validatesAgainstTheStoredClassYear() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);
        UserEntity user = user(uid);
        GuideService service = service();

        // Stored: entry 2023, class 2027 (inside [2024, 2029]).
        service.updateProfile(user, guideRequestWith(2023, "2027"));
        stubStoredState(uid);

        // Moving enrolment to 2026 makes the STORED 2027 illegal ([2027, 2032] starts at 2027 —
        // so pick 2016, whose window [2017, 2022] excludes it outright).
        GuideProfileUpdateRequest onlyEntryYear = guideRequestWith(2016, null);
        assertThrows(ValidationException.class, () -> service.updateProfile(user, onlyEntryYear));
    }

    @Test
    void updateProfile_changingOnlyClassYear_validatesAgainstTheStoredEntryYear() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);
        UserEntity user = user(uid);
        GuideService service = service();

        service.updateProfile(user, guideRequestWith(2023, "2027"));
        stubStoredState(uid);

        // 2035 is outside [2024, 2029] derived from the STORED entry year.
        assertThrows(
                ValidationException.class,
                () -> service.updateProfile(user, guideRequestWith(null, "2035")));

        // 2028 is inside it, and must be accepted without resending entryYear.
        service.updateProfile(user, guideRequestWith(null, "2028"));
    }

    /**
     * classYear's ceiling is entryYear + maxYearsToGraduate(DEGREE), so degree is the third input
     * to the same rule. Narrowing the degree can invalidate a stored pair that never changed:
     * bachelor's (2020, 2026) is legal at +6, and the same years under a master's (+3) are not.
     *
     * <p>This passes because {@code degree} is REQUIRED on this path — GuideService rejects a
     * missing one before validation, so the request always carries it and there is nothing to
     * merge. If degree ever becomes genuinely optional, the merge in this method must widen from a
     * pair to a triple, and this test is what will fail first.
     */
    @Test
    void updateProfile_narrowingTheDegree_revalidatesTheStoredYearsAgainstIt() {
        UUID uid = UUID.randomUUID();
        when(universities.existsById(UUID.fromString(UNIVERSITY_ID))).thenReturn(true);
        UserEntity user = user(uid);
        GuideService service = service();

        service.updateProfile(user, guideRequestWith(2020, "2026", "Bachelor's Degree"));
        stubStoredState(uid);

        assertThrows(
                ValidationException.class,
                () -> service.updateProfile(user, guideRequestWith(null, null, "Master's Degree")));
    }

    @Test
    void update_422_whenFirstNameHasInvalidCharacters() {
        // A digit in the name → NameRules rejects before any other field is looked at.
        GuideProfileUpdateRequest r =
                new GuideProfileUpdateRequest(
                        "Ann3",
                        null,
                        UUID.randomUUID().toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        null);
        var ex = badRequest(() -> service().updateProfile(user(UUID.randomUUID()), r));
        assertInstanceOf(ValidationException.class, ex);
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
                        " ",
                        " ",
                        uni.toString(),
                        "CS",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "Bachelor's Degree",
                        null);

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
                        false,
                        "Bachelor's Degree",
                        null);

        GuideProfileResponse res = service().updateProfile(u, r);

        assertEquals(List.of("en-US"), res.spokenLanguages());
    }

    @Test
    void update_tourTopicsWithNullAndBlankEntriesSkipped() {
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
                        false,
                        "Bachelor's Degree",
                        null);

        GuideProfileResponse res = service().updateProfile(u, r);

        assertEquals(List.of("GENERAL_CAMPUS"), res.tourTopics());
    }

    @Test
    void update_universityIdBlank_422Required() {
        UUID uid = UUID.randomUUID();
        var ex =
                badRequest(
                        () ->
                                service()
                                        .updateProfile(
                                                user(uid), req("   ", "CS", null, null, false)));
        assertInstanceOf(ValidationException.class, ex);
    }

    @Test
    void update_submitNull_treatedAsDraft() {
        UUID uid = UUID.randomUUID();
        UUID uni = UUID.randomUUID();
        when(universities.existsById(uni)).thenReturn(true);
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, null, null));

        verify(guides).save(any());
        verifyNoInteractions(roleGrant);
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
                                                req(uni.toString(), "CS", null, "noatsign", true)));
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
                                u,
                                submitReq(
                                        uni.toString(),
                                        "I lead weekly campus tours for prospective students.",
                                        List.of("GENERAL_CAMPUS")));

        assertEquals("PENDING", res.guideStatus());
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

        service().updateProfile(user(uid), req(uni.toString(), "CS", null, null, false));

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
                        guideUniversities,
                        universities,
                        participants,
                        users,
                        roleGrant,
                        schools,
                        campusImages,
                        badMapper,
                        rules);
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
                        false,
                        "Bachelor's Degree",
                        null);

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
                        guideUniversities,
                        universities,
                        participants,
                        users,
                        roleGrant,
                        schools,
                        campusImages,
                        badMapper,
                        rules);
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setSpokenLanguages("[\"en-US\"]"); // non-blank → readValue invoked → throws → []
        profile.setTourTopics("[\"GENERAL_CAMPUS\"]");
        when(guides.findByUserId(uid)).thenReturn(Optional.of(profile));

        GuideProfileResponse res = svc.getProfile(u);

        assertEquals(List.of(), res.spokenLanguages());
        assertEquals(List.of(), res.tourTopics());
    }
}
