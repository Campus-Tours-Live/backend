package com.CampusToursLive.domain.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TourOfferingService — the first endpoint set that exercises the live-action gate: a pending guide
 * may PREPARE a draft, but going live (activate) requires application_status == APPROVED.
 */
@ExtendWith(MockitoExtension.class)
class TourOfferingServiceTest {

    @Mock TourOfferingRepository offerings;
    @Mock GuideProfileRepository guides;
    @Mock UniversityRepository universities;

    private TourOfferingService service() {
        return new TourOfferingService(offerings, guides, universities, new ObjectMapper());
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileEntity guide(UUID id, GuideApplicationStatus status) {
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(id);
        g.setApplicationStatus(status);
        return g;
    }

    @Test
    void activate_throws403_whenApplicationNotApproved() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        when(guides.findByUserId(uid))
                .thenReturn(Optional.of(guide(gid, GuideApplicationStatus.PENDING_REVIEW)));

        assertThrows(
                ForbiddenException.class, () -> service().activate(user(uid), UUID.randomUUID()));
        // Gate fires before any offering lookup.
        verifyNoInteractions(offerings);
    }

    @Test
    void activate_setsActive_whenApproved() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        TourOfferingEntity draft = new TourOfferingEntity();
        draft.setId(oid);
        draft.setGuideId(gid);
        draft.setStatus(TourStatus.DRAFT);
        when(guides.findByUserId(uid))
                .thenReturn(Optional.of(guide(gid, GuideApplicationStatus.APPROVED)));
        when(offerings.findByIdAndGuideId(oid, gid)).thenReturn(Optional.of(draft));
        when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TourOfferingResponse res = service().activate(user(uid), oid);
        assertEquals("ACTIVE", res.status());
        assertEquals(TourStatus.ACTIVE, draft.getStatus());
    }

    @Test
    void create_allowsDraft_whileApplicationPending() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        when(guides.findByUserId(uid))
                .thenReturn(Optional.of(guide(gid, GuideApplicationStatus.PENDING_REVIEW)));
        when(universities.existsById(any())).thenReturn(true);
        when(offerings.existsByGuideIdAndSlug(eq(gid), anyString())).thenReturn(false);
        when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOfferingRequest req =
                new CreateOfferingRequest(
                        "Campus highlights walk",
                        UUID.randomUUID().toString(),
                        "GENERAL_CAMPUS",
                        60,
                        5000L,
                        null,
                        List.of("en-US"));

        TourOfferingResponse res = service().create(user(uid), req);
        assertEquals("DRAFT", res.status());
    }

    // ---------- helpers for the validation/branch cases ----------

    private static final String UNI = UUID.randomUUID().toString();

    /** A fully valid request; individual tests vary one field to a bad value. */
    private static CreateOfferingRequest validReq(String title) {
        return new CreateOfferingRequest(
                title, UNI, "GENERAL_CAMPUS", 60, 5000L, null, List.of("en-US"));
    }

    private void stubPendingGuide(UUID uid, UUID gid) {
        when(guides.findByUserId(uid))
                .thenReturn(Optional.of(guide(gid, GuideApplicationStatus.PENDING_REVIEW)));
    }

    private void stubApprovedGuide(UUID uid, UUID gid) {
        when(guides.findByUserId(uid))
                .thenReturn(Optional.of(guide(gid, GuideApplicationStatus.APPROVED)));
    }

    private static TourOfferingEntity offering(UUID id, UUID gid, TourStatus status) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(id);
        o.setGuideId(gid);
        o.setStatus(status);
        return o;
    }

    // ---------- create: validation branches ----------

    @Test
    void create_throws422_whenNoGuideProfile() {
        UUID uid = UUID.randomUUID();
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        assertThrows(
                ValidationException.class, () -> service().create(user(uid), validReq("Walk")));
        verifyNoInteractions(offerings);
    }

    @Test
    void create_throws422_whenUniversityIdBlank() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        CreateOfferingRequest req =
                new CreateOfferingRequest("Walk", "  ", "GENERAL_CAMPUS", 60, 5000L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), req));
    }

    @Test
    void create_throws422_whenUniversityIdNotUuid() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        CreateOfferingRequest req =
                new CreateOfferingRequest(
                        "Walk", "not-a-uuid", "GENERAL_CAMPUS", 60, 5000L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), req));
    }

    @Test
    void create_throws422_whenUniversityUnknown() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(false);
        assertThrows(
                ValidationException.class, () -> service().create(user(uid), validReq("Walk")));
    }

    @Test
    void create_throws422_whenTitleBlank() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        CreateOfferingRequest req =
                new CreateOfferingRequest("   ", UNI, "GENERAL_CAMPUS", 60, 5000L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), req));
    }

    @Test
    void create_throws422_whenTopicInvalid() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        CreateOfferingRequest req =
                new CreateOfferingRequest("Walk", UNI, "NOT_A_TOPIC", 60, 5000L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), req));
    }

    @Test
    void create_throws422_whenDurationNotAllowed() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        CreateOfferingRequest req =
                new CreateOfferingRequest("Walk", UNI, "GENERAL_CAMPUS", 25, 5000L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), req));
    }

    @Test
    void create_throws422_whenPriceOutOfRange() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        // Below the 2000 floor.
        CreateOfferingRequest low =
                new CreateOfferingRequest("Walk", UNI, "GENERAL_CAMPUS", 60, 100L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), low));
        // Above the 20000 ceiling.
        CreateOfferingRequest high =
                new CreateOfferingRequest("Walk", UNI, "GENERAL_CAMPUS", 60, 999999L, null, null);
        assertThrows(ValidationException.class, () -> service().create(user(uid), high));
    }

    @Test
    void create_throws422_whenSlugDuplicate() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        when(offerings.existsByGuideIdAndSlug(eq(gid), anyString())).thenReturn(true);
        assertThrows(
                ValidationException.class, () -> service().create(user(uid), validReq("Walk")));
    }

    @Test
    void create_persistsDerivedFields_onHappyPath() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        when(offerings.existsByGuideIdAndSlug(eq(gid), anyString())).thenReturn(false);
        when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOfferingRequest req =
                new CreateOfferingRequest(
                        "Campus Highlights Walk!",
                        UNI,
                        "GENERAL_CAMPUS",
                        60,
                        5000L,
                        "  great tour  ",
                        java.util.Arrays.asList("en-US", "", null));

        service().create(user(uid), req);

        ArgumentCaptor<TourOfferingEntity> cap = ArgumentCaptor.forClass(TourOfferingEntity.class);
        verify(offerings).save(cap.capture());
        TourOfferingEntity saved = cap.getValue();
        assertEquals(TourStatus.DRAFT, saved.getStatus());
        assertEquals("campus-highlights-walk", saved.getSlug());
        assertEquals("great tour", saved.getDescription()); // trimmed
        assertTrue(saved.getLanguages().contains("en-US")); // blanks/nulls filtered, JSON written
    }

    @Test
    void create_slugFallsBackToTour_whenTitleHasNoAlphanumerics() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(universities.existsById(any())).thenReturn(true);
        when(offerings.existsByGuideIdAndSlug(eq(gid), anyString())).thenReturn(false);
        when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TourOfferingResponse res = service().create(user(uid), validReq("!!!"));
        assertEquals("tour", res.slug());
    }

    // ---------- activate: remaining branches ----------

    @Test
    void activate_throws422_whenNoGuideProfile() {
        UUID uid = UUID.randomUUID();
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        assertThrows(
                ValidationException.class, () -> service().activate(user(uid), UUID.randomUUID()));
        verifyNoInteractions(offerings);
    }

    @Test
    void activate_isIdempotent_whenAlreadyActive() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        stubApprovedGuide(uid, gid);
        when(offerings.findByIdAndGuideId(oid, gid))
                .thenReturn(Optional.of(offering(oid, gid, TourStatus.ACTIVE)));

        TourOfferingResponse res = service().activate(user(uid), oid);
        assertEquals("ACTIVE", res.status());
        verify(offerings, never()).save(any());
    }

    @Test
    void activate_allowsPausedToActive() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        stubApprovedGuide(uid, gid);
        TourOfferingEntity paused = offering(oid, gid, TourStatus.PAUSED);
        when(offerings.findByIdAndGuideId(oid, gid)).thenReturn(Optional.of(paused));
        when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TourOfferingResponse res = service().activate(user(uid), oid);
        assertEquals("ACTIVE", res.status());
        assertEquals(TourStatus.ACTIVE, paused.getStatus());
    }

    @Test
    void activate_throws422_whenArchived() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        stubApprovedGuide(uid, gid);
        when(offerings.findByIdAndGuideId(oid, gid))
                .thenReturn(Optional.of(offering(oid, gid, TourStatus.ARCHIVED)));

        assertThrows(ValidationException.class, () -> service().activate(user(uid), oid));
        verify(offerings, never()).save(any());
    }

    @Test
    void activate_throws404_whenOfferingNotFound() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        stubApprovedGuide(uid, gid);
        when(offerings.findByIdAndGuideId(oid, gid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().activate(user(uid), oid));
    }

    // ---------- listOwn ----------

    @Test
    void listOwn_returnsMappedOfferings() {
        UUID uid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        stubPendingGuide(uid, gid);
        when(offerings.findByGuideId(gid))
                .thenReturn(List.of(offering(oid, gid, TourStatus.DRAFT)));

        List<TourOfferingResponse> res = service().listOwn(user(uid));
        assertEquals(1, res.size());
        assertEquals(oid.toString(), res.get(0).id());
        assertEquals("DRAFT", res.get(0).status());
    }

    @Test
    void listOwn_throws422_whenNoGuideProfile() {
        UUID uid = UUID.randomUUID();
        when(guides.findByUserId(uid)).thenReturn(Optional.empty());
        assertThrows(ValidationException.class, () -> service().listOwn(user(uid)));
    }
}
