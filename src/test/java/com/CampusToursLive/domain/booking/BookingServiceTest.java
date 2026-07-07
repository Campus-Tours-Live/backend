package com.CampusToursLive.domain.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookings;
    @Mock TourOfferingRepository offerings;
    @Mock GuideProfileRepository guides;
    @Mock UserRepository users;
    @Mock UniversityRepository universities;
    @Mock BookingStatusHistoryRepository statusHistory;

    private BookingService service() {
        return new BookingService(bookings, offerings, guides, users, universities, statusHistory);
    }

    // ── entity builders ──────────────────────────────────────────────────────

    private static BookingEntity booking(
            UUID id,
            UUID guideProfileId,
            UUID offeringId,
            UUID universityId,
            BookingStatus status,
            Instant start,
            Instant end) {
        BookingEntity b = new BookingEntity();
        b.setId(id);
        b.setParticipantUserId(UUID.randomUUID());
        b.setGuideId(guideProfileId);
        b.setTourOfferingId(offeringId);
        b.setUniversityId(universityId);
        b.setStatus(status);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(end);
        b.setDisplayTimezone("America/Los_Angeles");
        b.setBasePriceCents(5000L);
        b.setCurrency("USD");
        return b;
    }

    private static TourOfferingEntity offering(UUID id, String title) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(id);
        o.setTitle(title);
        return o;
    }

    private static GuideProfileEntity guideProfile(UUID id, UUID userId) {
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(id);
        g.setUserId(userId);
        return g;
    }

    private static UserEntity user(UUID id, String displayName) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setDisplayName(displayName);
        return u;
    }

    private static UniversityEntity university(UUID id, String name) {
        UniversityEntity u = new UniversityEntity();
        u.setId(id);
        u.setName(name);
        return u;
    }

    // ── getNextTour ──────────────────────────────────────────────────────────

    @Test
    void getNextTour_returnsEmpty_whenNoConfirmedBookingExists() {
        UUID uid = UUID.randomUUID();
        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertTrue(service().getNextTour(uid).isEmpty());
    }

    @Test
    void getNextTour_returnsMappedResponse_whenBookingFound() {
        UUID uid = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        Instant end = start.plus(60, ChronoUnit.MINUTES);

        BookingEntity b =
                booking(
                        bookingId,
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.CONFIRMED,
                        start,
                        end);
        b.setParticipantUserId(uid);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(offerings.findById(offeringId))
                .thenReturn(Optional.of(offering(offeringId, "Campus Walk")));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Jane Guide")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "Test University")));

        BookingDetailResponse resp = service().getNextTour(uid).orElseThrow();
        assertEquals(bookingId.toString(), resp.id());
        assertEquals("CONFIRMED", resp.status());
        assertEquals(start.toString(), resp.scheduledAt());
        assertEquals("America/Los_Angeles", resp.timezone());
        assertEquals(offeringId.toString(), resp.offeringId());
        assertEquals("Campus Walk", resp.offeringTitle());
        assertEquals("Jane Guide", resp.guideName());
        assertNull(resp.guideResponseDeadline());
        assertEquals("Test University", resp.universityName());
        assertEquals(60, resp.durationMin());
        assertEquals(5000L, resp.priceCents());
        assertEquals("USD", resp.currency());
    }

    @Test
    void getNextTour_onlyQueriesConfirmedStatus() {
        UUID uid = UUID.randomUUID();
        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.empty());

        service().getNextTour(uid);

        verify(bookings)
                .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                        eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class));
        verify(bookings, never())
                .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                        any(), any(), any());
    }

    // ── getUpcomingBookings ──────────────────────────────────────────────────

    @Test
    void getUpcomingBookings_returnsEmptyList_whenNoneFound() {
        UUID uid = UUID.randomUUID();
        when(bookings
                        .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), any(), any(Instant.class)))
                .thenReturn(List.of());

        assertTrue(service().getUpcomingBookings(uid).isEmpty());
    }

    @Test
    void getUpcomingBookings_returnsMappedList() {
        UUID uid = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-01T14:00:00Z");
        Instant end = start.plus(45, ChronoUnit.MINUTES);
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.PENDING_GUIDE_ACCEPTANCE,
                        start,
                        end);
        b.setParticipantUserId(uid);

        when(bookings
                        .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), any(), any(Instant.class)))
                .thenReturn(List.of(b));
        when(offerings.findById(offeringId))
                .thenReturn(Optional.of(offering(offeringId, "Lab Tour")));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Alex Guide")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "Tech U")));

        List<BookingDetailResponse> result = service().getUpcomingBookings(uid);
        assertEquals(1, result.size());
        assertEquals("WAITING_FOR_GUIDE", result.get(0).status());
        assertEquals(45, result.get(0).durationMin());
        assertEquals("Lab Tour", result.get(0).offeringTitle());
        assertEquals("Alex Guide", result.get(0).guideName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUpcomingBookings_includesAllFourExpectedStatuses() {
        UUID uid = UUID.randomUUID();
        ArgumentCaptor<List<BookingStatus>> captor =
                (ArgumentCaptor<List<BookingStatus>>)
                        (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        when(bookings
                        .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), captor.capture(), any(Instant.class)))
                .thenReturn(List.of());

        service().getUpcomingBookings(uid);

        List<BookingStatus> statuses = captor.getValue();
        assertTrue(statuses.contains(BookingStatus.CONFIRMED));
        assertTrue(statuses.contains(BookingStatus.PENDING_GUIDE_ACCEPTANCE));
        assertTrue(statuses.contains(BookingStatus.PENDING_PAYMENT_AUTH));
        assertTrue(statuses.contains(BookingStatus.PAYMENT_ACTION_REQUIRED));
    }

    // ── getPendingActions ────────────────────────────────────────────────────

    @Test
    void getPendingActions_mapsAllThreeCountsToResponse() {
        UUID uid = UUID.randomUUID();
        when(bookings.countByParticipantUserIdAndStatusIn(
                        eq(uid),
                        argThat(list -> list.contains(BookingStatus.PENDING_PAYMENT_AUTH))))
                .thenReturn(3L);
        when(bookings.countByParticipantUserIdAndStatusIn(
                        eq(uid),
                        argThat(
                                list ->
                                        list.contains(BookingStatus.PENDING_GUIDE_ACCEPTANCE)
                                                && !list.contains(
                                                        BookingStatus.PENDING_PAYMENT_AUTH))))
                .thenReturn(2L);
        when(bookings.countCompletedWithoutReview(uid)).thenReturn(1L);

        PendingActionsResponse resp = service().getPendingActions(uid);
        assertEquals(3L, resp.paymentsToFinish());
        assertEquals(2L, resp.waitingForGuide());
        assertEquals(1L, resp.reviewsToWrite());
    }

    @Test
    void getPendingActions_allZeros_whenNoOutstandingActions() {
        UUID uid = UUID.randomUUID();
        when(bookings.countByParticipantUserIdAndStatusIn(eq(uid), any())).thenReturn(0L);
        when(bookings.countCompletedWithoutReview(uid)).thenReturn(0L);

        PendingActionsResponse resp = service().getPendingActions(uid);
        assertEquals(0L, resp.paymentsToFinish());
        assertEquals(0L, resp.waitingForGuide());
        assertEquals(0L, resp.reviewsToWrite());
    }

    // ── toDetailResponse: field mapping and fallbacks ────────────────────────

    @Test
    void toDetailResponse_computesDurationFromBookingTimestamps() {
        UUID uid = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        // 90-minute tour — not reading from offering
        BookingEntity b =
                buildBookingWithFullLookupStubs(
                        uid, BookingStatus.CONFIRMED, start, start.plus(90, ChronoUnit.MINUTES));

        BookingDetailResponse resp = service().getNextTour(uid).orElseThrow();
        assertEquals(90, resp.durationMin());
    }

    @Test
    void toDetailResponse_guideResponseDeadline_isIsoString_whenSet() {
        UUID uid = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        Instant deadline = Instant.parse("2026-06-30T18:00:00Z");
        BookingEntity b =
                buildBookingWithFullLookupStubs(
                        uid, BookingStatus.CONFIRMED, start, start.plus(60, ChronoUnit.MINUTES));
        b.setGuideResponseDeadlineAt(deadline);

        BookingDetailResponse resp = service().getNextTour(uid).orElseThrow();
        assertEquals(deadline.toString(), resp.guideResponseDeadline());
    }

    @Test
    void toDetailResponse_guideResponseDeadline_isNull_whenNotSet() {
        UUID uid = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        // guideResponseDeadlineAt not set on the booking (defaults to null)
        buildBookingWithFullLookupStubs(
                uid, BookingStatus.CONFIRMED, start, start.plus(60, ChronoUnit.MINUTES));

        BookingDetailResponse resp = service().getNextTour(uid).orElseThrow();
        assertNull(resp.guideResponseDeadline());
    }

    @Test
    void toDetailResponse_offeringTitle_defaultsToTour_whenOfferingNotFound() {
        UUID uid = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.CONFIRMED,
                        start,
                        start.plus(60, ChronoUnit.MINUTES));
        b.setParticipantUserId(uid);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(offerings.findById(offeringId)).thenReturn(Optional.empty());
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Guide")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "U")));

        assertEquals("Tour", service().getNextTour(uid).orElseThrow().offeringTitle());
    }

    @Test
    void toDetailResponse_guideName_defaultsToEmpty_whenGuideProfileNotFound() {
        UUID uid = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.CONFIRMED,
                        start,
                        start.plus(60, ChronoUnit.MINUTES));
        b.setParticipantUserId(uid);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(guides.findById(guideProfileId)).thenReturn(Optional.empty());
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering(offeringId, "Tour")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "U")));

        assertEquals("", service().getNextTour(uid).orElseThrow().guideName());
        // Short-circuits before reaching UserRepository
        verifyNoInteractions(users);
    }

    @Test
    void toDetailResponse_guideName_defaultsToEmpty_whenUserNotFound() {
        UUID uid = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.CONFIRMED,
                        start,
                        start.plus(60, ChronoUnit.MINUTES));
        b.setParticipantUserId(uid);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.empty());
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering(offeringId, "Tour")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "U")));

        assertEquals("", service().getNextTour(uid).orElseThrow().guideName());
    }

    @Test
    void toDetailResponse_universityName_defaultsToEmpty_whenUniversityNotFound() {
        UUID uid = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        BookingStatus.CONFIRMED,
                        start,
                        start.plus(60, ChronoUnit.MINUTES));
        b.setParticipantUserId(uid);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(uid), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering(offeringId, "Tour")));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Guide")));
        when(universities.findById(universityId)).thenReturn(Optional.empty());

        assertEquals("", service().getNextTour(uid).orElseThrow().universityName());
    }

    // ── createBooking ────────────────────────────────────────────────────────

    @Test
    void createBooking_happyPath_persistsSnapshotAndAuditRow() {
        UserEntity participant = user(UUID.randomUUID(), "Pat Participant");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "Jane Guide")));
        when(bookings
                        .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                eq(ctx.guideProfileId()), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                eq(participant.getId()), any(), any(), any()))
                .thenReturn(false);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingDetailResponse resp =
                service()
                        .createBooking(
                                participant,
                                new CreateBookingRequest(
                                        ctx.offeringId().toString(),
                                        start.toString(),
                                        "America/Los_Angeles",
                                        "  meet at the fountain  "));

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        BookingEntity b = saved.getValue();
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, b.getStatus());
        assertEquals(AcceptanceMode.MANUAL, b.getAcceptanceModeSnap());
        assertEquals(participant.getId(), b.getParticipantUserId());
        assertEquals(ctx.guideProfileId(), b.getGuideId());
        assertEquals(ctx.offeringId(), b.getTourOfferingId());
        assertEquals(ctx.universityId(), b.getUniversityId());
        assertEquals(start, b.getScheduledStartAt());
        assertEquals(start.plus(60, ChronoUnit.MINUTES), b.getScheduledEndAt());
        // reserved interval = scheduled + 15-min post-tour buffer
        assertEquals(start, b.getReservedStartAt());
        assertEquals(start.plus(75, ChronoUnit.MINUTES), b.getReservedEndAt());
        assertEquals("America/Los_Angeles", b.getDisplayTimezone());
        assertNotNull(b.getGuideResponseDeadlineAt());
        assertTrue(b.getBookingNumber().startsWith("BK-"));
        assertEquals("BK-".length() + 10, b.getBookingNumber().length());
        // price snapshot: no fees while payments are unbuilt
        assertEquals(5000L, b.getBasePriceCents());
        assertEquals(5000L, b.getTotalCents());
        assertEquals(5000L, b.getGuideAmountCents());
        assertEquals(0L, b.getPlatformFeeCents());
        assertEquals(0L, b.getServiceFeeCents());
        assertEquals(0L, b.getTaxCents());
        assertEquals("USD", b.getCurrency());
        assertEquals("meet at the fountain", b.getParticipantNotes());

        ArgumentCaptor<BookingStatusHistoryEntity> audit =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(statusHistory).save(audit.capture());
        assertEquals(b.getId(), audit.getValue().getBookingId());
        assertNull(audit.getValue().getPreviousStatus());
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, audit.getValue().getNewStatus());
        assertEquals(BookingActor.PARTICIPANT, audit.getValue().getActorType());
        assertEquals(participant.getId(), audit.getValue().getActorUserId());
        assertEquals("PARTICIPANT_CREATED", audit.getValue().getReasonCode());

        assertEquals("WAITING_FOR_GUIDE", resp.status());
        assertEquals(60, resp.durationMin());
        assertEquals("Jane Guide", resp.guideName());
    }

    @Test
    void createBooking_blankNotes_storedAsNull() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "G")));
        stubNoOverlaps();

        service().createBooking(participant, validRequest(ctx, "   "));

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getParticipantNotes());
    }

    @Test
    void createBooking_rejectsMissingOrMalformedOfferingId() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(null, null, null, null)));
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest("not-a-uuid", null, null, null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_unknownOffering_isNotFound() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offeringId.toString(), null, null, null)));
    }

    @Test
    void createBooking_nonActiveOffering_isNotFound() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID offeringId = UUID.randomUUID();
        TourOfferingEntity o = offering(offeringId, "Draft Tour");
        o.setStatus(TourStatus.DRAFT);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(o));

        assertThrows(
                NotFoundException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offeringId.toString(), null, null, null)));
    }

    @Test
    void createBooking_unapprovedGuide_isNotBookable() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID offeringId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        TourOfferingEntity o = offering(offeringId, "Tour");
        o.setStatus(TourStatus.ACTIVE);
        o.setGuideId(guideProfileId);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(o));
        GuideProfileEntity g = guideProfile(guideProfileId, UUID.randomUUID());
        g.setApplicationStatus(GuideApplicationStatus.PENDING_REVIEW);
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offeringId.toString(), null, null, null)));
    }

    @Test
    void createBooking_nonActiveUniversity_isNotBookable() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID offeringId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();
        TourOfferingEntity o = offering(offeringId, "Tour");
        o.setStatus(TourStatus.ACTIVE);
        o.setGuideId(guideProfileId);
        o.setUniversityId(universityId);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(o));
        GuideProfileEntity g = guideProfile(guideProfileId, UUID.randomUUID());
        g.setApplicationStatus(GuideApplicationStatus.APPROVED);
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));
        UniversityEntity u = university(universityId, "Paused U");
        u.setStatus(UniversityStatus.PAUSED);
        when(universities.findById(universityId)).thenReturn(Optional.of(u));

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offeringId.toString(), null, null, null)));
    }

    @Test
    void createBooking_ownTour_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Guide-as-participant");
        Bookable ctx = stubBookableOffering(participant.getId());

        assertThrows(
                ValidationException.class,
                () -> service().createBooking(participant, validRequest(ctx, null)));
    }

    @Test
    void createBooking_rejectsMissingOrMalformedStart() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(), null, "UTC", null)));
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(),
                                                "tomorrow at noon",
                                                "UTC",
                                                null)));
    }

    @Test
    void createBooking_enforcesNoticeAndAdvanceWindows() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();

        // Less than 24h notice
        String tooSoon = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(),
                                                tooSoon,
                                                "UTC",
                                                null)));

        // More than 30 days out
        String tooFar = Instant.now().plus(45, ChronoUnit.DAYS).toString();
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(), tooFar, "UTC", null)));
    }

    @Test
    void createBooking_rejectsMissingOrInvalidTimezone() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        String start = Instant.now().plus(3, ChronoUnit.DAYS).toString();

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(), start, null, null)));
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(),
                                                start,
                                                "Mars/Olympus_Mons",
                                                null)));
    }

    @Test
    void createBooking_guideSlotTaken_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(bookings
                        .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                eq(ctx.guideProfileId()), any(), any(), any()))
                .thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().createBooking(participant, validRequest(ctx, null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_participantOverlap_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(bookings
                        .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                eq(ctx.guideProfileId()), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                eq(participant.getId()), any(), any(), any()))
                .thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().createBooking(participant, validRequest(ctx, null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_lostInsertRace_surfacesAsSlotTaken_andWritesNoAudit() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        stubNoOverlaps();
        when(bookings.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("excl_guide_no_overlap"));

        assertThrows(
                ValidationException.class,
                () -> service().createBooking(participant, validRequest(ctx, null)));
        verifyNoInteractions(statusHistory);
    }

    @Test
    void createBooking_notesOverLengthCap_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();

        assertThrows(
                ValidationException.class,
                () -> service().createBooking(participant, validRequest(ctx, "x".repeat(1001))));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancelBooking_reasonOverLengthCap_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b = upcomingBooking(participant.getId(), BookingStatus.CONFIRMED);
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .cancelBooking(
                                        participant,
                                        b.getId(),
                                        new CancelBookingRequest("x".repeat(1001))));
        verify(bookings, never()).save(any());
    }

    // ── cancelBooking ────────────────────────────────────────────────────────

    @Test
    void cancelBooking_confirmedBooking_isCancelledWithAuditRow() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b = upcomingBooking(participant.getId(), BookingStatus.CONFIRMED);
        stubDetailLookups(b);
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        BookingDetailResponse resp =
                service()
                        .cancelBooking(
                                participant,
                                b.getId(),
                                new CancelBookingRequest("  can't make it  "));

        assertEquals(BookingStatus.CANCELLED_BY_PARTICIPANT, b.getStatus());
        assertEquals(BookingActor.PARTICIPANT, b.getCancellationActor());
        assertEquals("can't make it", b.getCancellationReason());
        assertNotNull(b.getCancelledAt());
        verify(bookings).save(b);

        ArgumentCaptor<BookingStatusHistoryEntity> audit =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(statusHistory).save(audit.capture());
        assertEquals(BookingStatus.CONFIRMED, audit.getValue().getPreviousStatus());
        assertEquals(BookingStatus.CANCELLED_BY_PARTICIPANT, audit.getValue().getNewStatus());
        assertEquals("PARTICIPANT_CANCELLED", audit.getValue().getReasonCode());

        assertEquals("CANCELLED", resp.status());
    }

    @Test
    void cancelBooking_pendingGuideAcceptance_isCancellable_withoutBody() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b =
                upcomingBooking(participant.getId(), BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        stubDetailLookups(b);
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        service().cancelBooking(participant, b.getId(), null);

        assertEquals(BookingStatus.CANCELLED_BY_PARTICIPANT, b.getStatus());
        assertNull(b.getCancellationReason());
        verify(bookings).save(b);
    }

    @Test
    void cancelBooking_unknownOrForeignBooking_isNotFound() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID bookingId = UUID.randomUUID();
        when(bookings.findByIdAndParticipantUserId(bookingId, participant.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service().cancelBooking(participant, bookingId, null));
    }

    @Test
    void cancelBooking_alreadyCancelled_isIdempotent() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b =
                upcomingBooking(participant.getId(), BookingStatus.CANCELLED_BY_PARTICIPANT);
        stubDetailLookups(b);
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        BookingDetailResponse resp = service().cancelBooking(participant, b.getId(), null);

        assertEquals("CANCELLED", resp.status());
        verify(bookings, never()).save(any());
        verifyNoInteractions(statusHistory);
    }

    @Test
    void cancelBooking_terminalStatus_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b = upcomingBooking(participant.getId(), BookingStatus.COMPLETED);
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        assertThrows(
                ValidationException.class,
                () -> service().cancelBooking(participant, b.getId(), null));
        verify(bookings, never()).save(any());
    }

    @Test
    void cancelBooking_afterStart_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity b = upcomingBooking(participant.getId(), BookingStatus.CONFIRMED);
        b.setScheduledStartAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(bookings.findByIdAndParticipantUserId(b.getId(), participant.getId()))
                .thenReturn(Optional.of(b));

        assertThrows(
                ValidationException.class,
                () -> service().cancelBooking(participant, b.getId(), null));
        verify(bookings, never()).save(any());
    }

    // ── write-test fixtures ──────────────────────────────────────────────────

    /** Ids of a fully bookable offering wired into the lookup mocks. */
    private record Bookable(
            UUID offeringId, UUID guideProfileId, UUID guideUserId, UUID universityId) {}

    private Bookable stubBookableOffering() {
        return stubBookableOffering(UUID.randomUUID());
    }

    /**
     * Stubs an ACTIVE 60-min $50 offering by an APPROVED guide (owned by {@code guideUserId}) at an
     * ACTIVE university.
     */
    private Bookable stubBookableOffering(UUID guideUserId) {
        UUID offeringId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();

        TourOfferingEntity o = offering(offeringId, "Campus Walk");
        o.setStatus(TourStatus.ACTIVE);
        o.setGuideId(guideProfileId);
        o.setUniversityId(universityId);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(o));

        GuideProfileEntity g = guideProfile(guideProfileId, guideUserId);
        g.setApplicationStatus(GuideApplicationStatus.APPROVED);
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));

        UniversityEntity u = university(universityId, "Test University");
        u.setStatus(UniversityStatus.ACTIVE);
        when(universities.findById(universityId)).thenReturn(Optional.of(u));

        return new Bookable(offeringId, guideProfileId, guideUserId, universityId);
    }

    private void stubNoOverlaps() {
        when(bookings
                        .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                any(), any(), any(), any()))
                .thenReturn(false);
    }

    private static CreateBookingRequest validRequest(Bookable ctx, String notes) {
        return new CreateBookingRequest(
                ctx.offeringId().toString(),
                Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                "UTC",
                notes);
    }

    /** A booking 3 days out owned by the participant, in the given status. */
    private static BookingEntity upcomingBooking(UUID participantUserId, BookingStatus status) {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        status,
                        start,
                        start.plus(60, ChronoUnit.MINUTES));
        b.setParticipantUserId(participantUserId);
        return b;
    }

    /** Stubs the three name-resolution lookups used by toDetailResponse for this booking. */
    private void stubDetailLookups(BookingEntity b) {
        UUID guideUserId = UUID.randomUUID();
        when(offerings.findById(b.getTourOfferingId()))
                .thenReturn(Optional.of(offering(b.getTourOfferingId(), "Campus Walk")));
        when(guides.findById(b.getGuideId()))
                .thenReturn(Optional.of(guideProfile(b.getGuideId(), guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Jane Guide")));
        when(universities.findById(b.getUniversityId()))
                .thenReturn(Optional.of(university(b.getUniversityId(), "Test University")));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Creates a booking, stubs all three lookup repos with default values, and stubs the
     * findFirstBy... query to return it. Returns the entity so individual tests can tweak it (e.g.
     * set guideResponseDeadlineAt) before calling the service.
     */
    private BookingEntity buildBookingWithFullLookupStubs(
            UUID participantUserId, BookingStatus status, Instant start, Instant end) {
        UUID guideProfileId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID universityId = UUID.randomUUID();

        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        guideProfileId,
                        offeringId,
                        universityId,
                        status,
                        start,
                        end);
        b.setParticipantUserId(participantUserId);

        when(bookings
                        .findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                eq(participantUserId), eq(status), any(Instant.class)))
                .thenReturn(Optional.of(b));
        when(offerings.findById(offeringId))
                .thenReturn(Optional.of(offering(offeringId, "Campus Walk")));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guideProfile(guideProfileId, guideUserId)));
        when(users.findById(guideUserId)).thenReturn(Optional.of(user(guideUserId, "Jane Guide")));
        when(universities.findById(universityId))
                .thenReturn(Optional.of(university(universityId, "Test University")));

        return b;
    }
}
