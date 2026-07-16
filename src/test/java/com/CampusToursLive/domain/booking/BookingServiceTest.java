package com.CampusToursLive.domain.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
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
import java.time.Duration;
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
    @Mock GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    @Mock GuideBookingSettingsRepository settings;

    private BookingService service() {
        return new BookingService(
                bookings,
                offerings,
                guides,
                users,
                universities,
                statusHistory,
                availabilityOccurrences,
                settings);
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
    void createBooking_blankOfferingId_isRequired() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant, new CreateBookingRequest("   ", null, null)));
    }

    @Test
    void createBooking_blankScheduledStartAt_isRequired() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(), "   ", null)));
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
                                        participant, new CreateBookingRequest(null, null, null)));
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest("not-a-uuid", null, null)));
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
                                                offeringId.toString(), null, null)));
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
                                                offeringId.toString(), null, null)));
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
                                                offeringId.toString(), null, null)));
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
                                                offeringId.toString(), null, null)));
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
                                                ctx.offeringId().toString(), null, null)));
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(),
                                                "tomorrow at noon",
                                                null)));
    }

    @Test
    void createBooking_enforcesNoticeAndAdvanceWindows() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();

        // Less than 24h notice
        String tooSoon = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        ValidationException tooSoonEx =
                assertThrows(
                        ValidationException.class,
                        () ->
                                service()
                                        .createBooking(
                                                participant,
                                                new CreateBookingRequest(
                                                        ctx.offeringId().toString(),
                                                        tooSoon,
                                                        null)));
        // Default minNoticeMin (schema default 1440min = 24h, a whole number of hours) must still
        // read "24 hours" -- byte-identical to before the CTL-54 minutes-rendering fix.
        assertTrue(tooSoonEx.getMessage().contains("24 hours"));

        // More than 30 days out
        String tooFar = Instant.now().plus(45, ChronoUnit.DAYS).toString();
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(), tooFar, null)));
    }

    // ── createBooking: guide-configured notice/advance/buffers (CTL-54 design-gap fix) ─────────

    @Test
    void createBooking_customMaxAdvanceDays_acceptsBookingBeyondDefaultThirtyDayLimit() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "Jane Guide")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMaxAdvanceDays(180);
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();

        // 90 days out -- would 422 against the OLD hardcoded 30-day MAX_ADVANCE, but this guide's
        // own settings allow 180 days, so createBooking must accept it (the "listed-but-unbookable"
        // bug this fix closes: SlotGenerationService already honored maxAdvanceDays here).
        String ninetyDaysOut =
                Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString();

        BookingDetailResponse resp =
                service()
                        .createBooking(
                                participant,
                                new CreateBookingRequest(
                                        ctx.offeringId().toString(), ninetyDaysOut, null));

        assertEquals("WAITING_FOR_GUIDE", resp.status());
    }

    @Test
    void createBooking_customMinNoticeMin_rejectsBookingInsideGuidesStricterWindow() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMinNoticeMin((int) Duration.ofHours(48).toMinutes());
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));

        // 25h out -- would be ACCEPTED against the OLD hardcoded 24h MIN_NOTICE, but this guide's
        // own settings require 48h, so createBooking must reject it (the unenforced-policy half of
        // the bug: a guide who tightens notice must actually see it enforced at create).
        String twentyFiveHoursOut = Instant.now().plus(25, ChronoUnit.HOURS).toString();

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () ->
                                service()
                                        .createBooking(
                                                participant,
                                                new CreateBookingRequest(
                                                        ctx.offeringId().toString(),
                                                        twentyFiveHoursOut,
                                                        null)));
        assertTrue(ex.getMessage().contains("48"));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_minNoticeMinNotHourAligned_rendersMinutesNotTruncatedHours() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMinNoticeMin(90);
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));

        // 60 min out -- inside this guide's 90-min window. Duration.toHours() TRUNCATES 90 minutes
        // to "1 hours" (wrong value, wrong grammar); the message must read "90 minutes" instead.
        String sixtyMinutesOut = Instant.now().plus(60, ChronoUnit.MINUTES).toString();

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () ->
                                service()
                                        .createBooking(
                                                participant,
                                                new CreateBookingRequest(
                                                        ctx.offeringId().toString(),
                                                        sixtyMinutesOut,
                                                        null)));
        assertTrue(ex.getMessage().contains("90 minutes"));
        assertFalse(ex.getMessage().contains("1 hours"));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_customBuffers_reservedIntervalUsesGuidesOwnSettings() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "Jane Guide")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setBufferBeforeMin(30);
        guideSettings.setBufferAfterMin(45);
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        service()
                .createBooking(
                        participant,
                        new CreateBookingRequest(
                                ctx.offeringId().toString(), start.toString(), null));

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        BookingEntity b = saved.getValue();
        assertEquals(start.minus(30, ChronoUnit.MINUTES), b.getReservedStartAt());
        assertEquals(
                start.plus(60, ChronoUnit.MINUTES).plus(45, ChronoUnit.MINUTES),
                b.getReservedEndAt());
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

    // ── createBooking: acceptance mode from guide settings (CTL-34) ────────────────────────────

    @Test
    void createBooking_autoAcceptanceMode_confirmsImmediately() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "Jane Guide")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setAcceptanceMode(AcceptanceMode.AUTO);
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();

        BookingDetailResponse resp = service().createBooking(participant, validRequest(ctx, null));

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        BookingEntity b = saved.getValue();
        // AUTO → straight to CONFIRMED, confirmed_at set, no guide response deadline.
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
        assertEquals(AcceptanceMode.AUTO, b.getAcceptanceModeSnap());
        assertNotNull(b.getConfirmedAt());
        assertNull(b.getGuideResponseDeadlineAt());
        assertEquals("CONFIRMED", resp.status());

        ArgumentCaptor<BookingStatusHistoryEntity> audit =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(statusHistory).save(audit.capture());
        assertEquals(BookingStatus.CONFIRMED, audit.getValue().getNewStatus());
    }

    @Test
    void createBooking_manualAcceptanceMode_usesGuidesConfiguredResponseDeadline() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "Jane Guide")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setAcceptanceMode(AcceptanceMode.MANUAL);
        guideSettings.setResponseDeadlineMin(120); // not the 90-min schema default
        when(settings.findByGuideId(ctx.guideProfileId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();

        Instant before = Instant.now();
        BookingDetailResponse resp = service().createBooking(participant, validRequest(ctx, null));
        Instant after = Instant.now();

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        BookingEntity b = saved.getValue();
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, b.getStatus());
        assertEquals(AcceptanceMode.MANUAL, b.getAcceptanceModeSnap());
        assertNull(b.getConfirmedAt());
        // Deadline is now + the guide's configured 120 minutes (not the 90-min default).
        assertFalse(b.getGuideResponseDeadlineAt().isBefore(before.plus(120, ChronoUnit.MINUTES)));
        assertFalse(b.getGuideResponseDeadlineAt().isAfter(after.plus(120, ChronoUnit.MINUTES)));
        assertEquals("WAITING_FOR_GUIDE", resp.status());
    }

    @Test
    void createBooking_outsideAvailability_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        // Overrides the default (lenient) "covered" stub from stubBookableOffering(): this guide
        // has no availability occurrence containing the requested scheduled interval.
        when(availabilityOccurrences.existsContaining(eq(ctx.guideProfileId()), any(), any()))
                .thenReturn(false);

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().createBooking(participant, validRequest(ctx, null)));
        assertEquals(BookingService.OUTSIDE_AVAILABILITY_MESSAGE, ex.getMessage());
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

    // ── addCartItem ──────────────────────────────────────────────────────────

    @Test
    void addCartItem_savesDraft_withoutDeadline_andAuditRow() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(users.findById(ctx.guideUserId()))
                .thenReturn(Optional.of(user(ctx.guideUserId(), "G")));
        when(bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT))
                .thenReturn(0L);
        stubNoOverlaps();
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of());

        BookingDetailResponse resp = service().addCartItem(participant, validRequest(ctx, null));

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookings).saveAndFlush(saved.capture());
        assertEquals(BookingStatus.DRAFT, saved.getValue().getStatus());
        assertNull(saved.getValue().getGuideResponseDeadlineAt());
        assertEquals("DRAFT", resp.status());

        ArgumentCaptor<BookingStatusHistoryEntity> audit =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(statusHistory).save(audit.capture());
        assertNull(audit.getValue().getPreviousStatus());
        assertEquals(BookingStatus.DRAFT, audit.getValue().getNewStatus());
        assertEquals("CART_ITEM_ADDED", audit.getValue().getReasonCode());
    }

    @Test
    void addCartItem_fullCart_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        when(bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT))
                .thenReturn(10L);

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .addCartItem(
                                        participant,
                                        new CreateBookingRequest(
                                                UUID.randomUUID().toString(), null, null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void addCartItem_conflictWithHeldBooking_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT))
                .thenReturn(0L);
        when(bookings
                        .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                eq(ctx.guideProfileId()), any(), any(), any()))
                .thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> service().addCartItem(participant, validRequest(ctx, null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void addCartItem_conflictWithAnotherCartItem_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT))
                .thenReturn(1L);
        stubNoOverlaps();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(draftItem(participant.getId(), start, 60)));

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .addCartItem(
                                        participant,
                                        new CreateBookingRequest(
                                                ctx.offeringId().toString(),
                                                start.toString(),
                                                null)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void addCartItem_outsideAvailability_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Bookable ctx = stubBookableOffering();
        when(bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT))
                .thenReturn(0L);
        // Overrides the default (lenient) "covered" stub: proves the containment gate lives in
        // the shared buildDraftBooking, not just the create endpoint — a not-contained interval is
        // rejected at cart-add too.
        when(availabilityOccurrences.existsContaining(eq(ctx.guideProfileId()), any(), any()))
                .thenReturn(false);

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().addCartItem(participant, validRequest(ctx, null)));
        assertEquals(BookingService.OUTSIDE_AVAILABILITY_MESSAGE, ex.getMessage());
        verify(bookings, never()).saveAndFlush(any());
    }

    // ── getCart / removeCartItem ─────────────────────────────────────────────

    @Test
    void getCart_returnsDraftItems_withDraftDisplayStatus() {
        UUID uid = UUID.randomUUID();
        BookingEntity item = upcomingBooking(uid, BookingStatus.DRAFT);
        stubDetailLookups(item);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(uid, BookingStatus.DRAFT))
                .thenReturn(List.of(item));

        List<BookingDetailResponse> cart = service().getCart(uid);
        assertEquals(1, cart.size());
        assertEquals("DRAFT", cart.get(0).status());
    }

    @Test
    void removeCartItem_deletesDraft_andReturnsRemainingCart() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item = upcomingBooking(participant.getId(), BookingStatus.DRAFT);
        when(bookings.findByIdAndParticipantUserIdAndStatus(
                        item.getId(), participant.getId(), BookingStatus.DRAFT))
                .thenReturn(Optional.of(item));
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of());

        assertTrue(service().removeCartItem(participant, item.getId()).isEmpty());
        verify(bookings).delete(item);
    }

    @Test
    void removeCartItem_unknownOrNonDraft_isNotFound() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        UUID itemId = UUID.randomUUID();
        when(bookings.findByIdAndParticipantUserIdAndStatus(
                        itemId, participant.getId(), BookingStatus.DRAFT))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().removeCartItem(participant, itemId));
        verify(bookings, never()).delete(any());
    }

    // ── checkout ─────────────────────────────────────────────────────────────

    @Test
    void checkout_flipsAllDraftsToPending_withDeadlinesAndAuditRows() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start1 = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant start2 = Instant.now().plus(5, ChronoUnit.DAYS);
        BookingEntity i1 = draftItem(participant.getId(), start1, 60);
        BookingEntity i2 = draftItem(participant.getId(), start2, 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(i1, i2));
        UUID gu1 = stubCheckoutLookups(i1, TourStatus.ACTIVE);
        UUID gu2 = stubCheckoutLookups(i2, TourStatus.ACTIVE);
        when(users.findById(gu1)).thenReturn(Optional.of(user(gu1, "G1")));
        when(users.findById(gu2)).thenReturn(Optional.of(user(gu2, "G2")));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(i1, i2));

        List<BookingDetailResponse> resp = service().checkout(participant);

        assertEquals(2, resp.size());
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, i1.getStatus());
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, i2.getStatus());
        assertNotNull(i1.getGuideResponseDeadlineAt());
        assertNotNull(i2.getGuideResponseDeadlineAt());
        assertEquals("WAITING_FOR_GUIDE", resp.get(0).status());

        ArgumentCaptor<BookingStatusHistoryEntity> audit =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(statusHistory, times(2)).save(audit.capture());
        assertEquals(BookingStatus.DRAFT, audit.getAllValues().get(0).getPreviousStatus());
        assertEquals("CART_CHECKOUT", audit.getAllValues().get(0).getReasonCode());
    }

    @Test
    void checkout_autoAcceptanceMode_confirmsImmediately() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(3, ChronoUnit.DAYS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        UUID gu = stubCheckoutLookups(item, TourStatus.ACTIVE);
        when(users.findById(gu)).thenReturn(Optional.of(user(gu, "G")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setAcceptanceMode(AcceptanceMode.AUTO);
        when(settings.findByGuideId(item.getGuideId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(item));

        List<BookingDetailResponse> resp = service().checkout(participant);

        // The guide flipped to AUTO while the item sat in the cart → checkout confirms it.
        assertEquals(BookingStatus.CONFIRMED, item.getStatus());
        assertEquals(AcceptanceMode.AUTO, item.getAcceptanceModeSnap());
        assertNotNull(item.getConfirmedAt());
        assertNull(item.getGuideResponseDeadlineAt());
        assertEquals("CONFIRMED", resp.get(0).status());
    }

    @Test
    void checkout_emptyCart_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of());

        assertThrows(ValidationException.class, () -> service().checkout(participant));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_staleItemInsideNoticeWindow_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity stale =
                draftItem(participant.getId(), Instant.now().plus(2, ChronoUnit.HOURS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(stale));
        stubCheckoutLookups(stale, TourStatus.ACTIVE);

        assertThrows(ValidationException.class, () -> service().checkout(participant));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_offeringNoLongerActive_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(3, ChronoUnit.DAYS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        stubCheckoutLookups(item, TourStatus.PAUSED);

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service().checkout(participant));
        assertTrue(ex.getMessage().contains(item.getBookingNumber()));
        assertEquals(BookingStatus.DRAFT, item.getStatus());
    }

    @Test
    void checkout_itemsOverlappingEachOther_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        BookingEntity i1 = draftItem(participant.getId(), start, 60);
        BookingEntity i2 = draftItem(participant.getId(), start.plus(30, ChronoUnit.MINUTES), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(i1, i2));
        stubCheckoutLookups(i1, TourStatus.ACTIVE);
        stubCheckoutLookups(i2, TourStatus.ACTIVE);
        stubNoOverlaps();

        assertThrows(ValidationException.class, () -> service().checkout(participant));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_lostSlotRace_rollsBackWholeCart_andWritesNoAudit() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(3, ChronoUnit.DAYS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        stubCheckoutLookups(item, TourStatus.ACTIVE);
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("excl_guide_no_overlap"));

        assertThrows(ValidationException.class, () -> service().checkout(participant));
        verifyNoInteractions(statusHistory);
    }

    @Test
    void checkout_reSnapshotsPriceAndDuration_fromCurrentOffering() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // Carted at 60 min / 5000¢; the offering has since changed to 30 min / 9000¢.
        BookingEntity item = draftItem(participant.getId(), start, 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        UUID gu = stubCheckoutLookups(item, TourStatus.ACTIVE, 30, 9000L);
        when(users.findById(gu)).thenReturn(Optional.of(user(gu, "G")));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(item));

        List<BookingDetailResponse> resp = service().checkout(participant);

        assertEquals(9000L, item.getBasePriceCents());
        assertEquals(9000L, item.getTotalCents());
        assertEquals(9000L, item.getGuideAmountCents());
        assertEquals(start.plus(30, ChronoUnit.MINUTES), item.getScheduledEndAt());
        assertEquals(start.plus(45, ChronoUnit.MINUTES), item.getReservedEndAt());
        assertEquals(30, resp.get(0).durationMin());
        assertEquals(9000L, resp.get(0).priceCents());
    }

    // ── checkout: guide-configured notice/advance/buffers (CTL-54 design-gap fix — parity with
    // buildDraftBooking / createBooking, since the guide's settings can tighten OR loosen while an
    // item sits in the cart) ─────────────────────────────────────────────────────────────────

    @Test
    void checkout_customMinNoticeMin_rejectsItemInsideGuidesStricterWindow() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        // 25h out -- fine against the OLD hardcoded 24h MIN_NOTICE, but this guide's settings now
        // require 48h notice, so checkout must reject it.
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(25, ChronoUnit.HOURS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        stubCheckoutLookups(item, TourStatus.ACTIVE);
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMinNoticeMin((int) Duration.ofHours(48).toMinutes());
        when(settings.findByGuideId(item.getGuideId())).thenReturn(Optional.of(guideSettings));

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service().checkout(participant));
        assertTrue(ex.getMessage().contains(item.getBookingNumber()));
        assertTrue(ex.getMessage().contains("48 hours"));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_minNoticeMinNotHourAligned_rendersMinutesNotTruncatedHours() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        // 60 min out -- inside this guide's 90-min window. Duration.toHours() TRUNCATES 90 minutes
        // to "1 hours" (wrong value, wrong grammar); the message must read "90 minutes" instead.
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(60, ChronoUnit.MINUTES), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        stubCheckoutLookups(item, TourStatus.ACTIVE);
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMinNoticeMin(90);
        when(settings.findByGuideId(item.getGuideId())).thenReturn(Optional.of(guideSettings));

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service().checkout(participant));
        assertTrue(ex.getMessage().contains(item.getBookingNumber()));
        assertTrue(ex.getMessage().contains("90 minutes"));
        assertFalse(ex.getMessage().contains("1 hours"));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_customMaxAdvanceDays_acceptsItemBeyondDefaultThirtyDayLimit() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity item = draftItem(participant.getId(), start, 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        UUID gu = stubCheckoutLookups(item, TourStatus.ACTIVE);
        when(users.findById(gu)).thenReturn(Optional.of(user(gu, "G")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setMaxAdvanceDays(180);
        when(settings.findByGuideId(item.getGuideId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(item));

        List<BookingDetailResponse> resp = service().checkout(participant);

        assertEquals(1, resp.size());
        assertEquals(BookingStatus.PENDING_GUIDE_ACCEPTANCE, item.getStatus());
    }

    @Test
    void checkout_customBuffers_reReservesUsingGuidesOwnSettings() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity item = draftItem(participant.getId(), start, 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        UUID gu = stubCheckoutLookups(item, TourStatus.ACTIVE);
        when(users.findById(gu)).thenReturn(Optional.of(user(gu, "G")));
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setBufferBeforeMin(30);
        guideSettings.setBufferAfterMin(45);
        when(settings.findByGuideId(item.getGuideId())).thenReturn(Optional.of(guideSettings));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(item));

        service().checkout(participant);

        assertEquals(start.minus(30, ChronoUnit.MINUTES), item.getReservedStartAt());
        assertEquals(
                start.plus(60, ChronoUnit.MINUTES).plus(45, ChronoUnit.MINUTES),
                item.getReservedEndAt());
    }

    @Test
    void checkout_availabilityRemovedSinceCartAdd_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(3, ChronoUnit.DAYS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        stubCheckoutLookups(item, TourStatus.ACTIVE);
        // The item was contained when it was added to the cart, but the guide has since edited
        // their availability so it no longer covers the scheduled time -- revalidateCartItem must
        // re-run the containment check at checkout, not just trust add-to-cart time.
        when(availabilityOccurrences.existsContaining(eq(item.getGuideId()), any(), any()))
                .thenReturn(false);

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service().checkout(participant));
        assertTrue(ex.getMessage().contains(item.getBookingNumber()));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_guideNoLongerApproved_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        BookingEntity item =
                draftItem(participant.getId(), Instant.now().plus(3, ChronoUnit.DAYS), 60);
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(item));
        TourOfferingEntity o = offering(item.getTourOfferingId(), "Tour");
        o.setStatus(TourStatus.ACTIVE);
        o.setGuideId(item.getGuideId());
        o.setUniversityId(item.getUniversityId());
        when(offerings.findById(item.getTourOfferingId())).thenReturn(Optional.of(o));
        GuideProfileEntity g = guideProfile(item.getGuideId(), UUID.randomUUID());
        g.setApplicationStatus(GuideApplicationStatus.SUSPENDED);
        when(guides.findById(item.getGuideId())).thenReturn(Optional.of(g));

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service().checkout(participant));
        assertTrue(ex.getMessage().contains(item.getBookingNumber()));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_sameGuideBufferOverlap_isRejected() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // Back-to-back with the SAME guide: scheduled windows touch but don't overlap, yet the
        // 15-min post-tour buffer makes the reserved intervals collide (guideClash only).
        BookingEntity i1 = draftItem(participant.getId(), start, 60);
        BookingEntity i2 = draftItem(participant.getId(), start.plus(60, ChronoUnit.MINUTES), 60);
        i2.setGuideId(i1.getGuideId());
        i2.setTourOfferingId(i1.getTourOfferingId());
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(i1, i2));
        stubCheckoutLookups(i1, TourStatus.ACTIVE);
        stubNoOverlaps();

        assertThrows(ValidationException.class, () -> service().checkout(participant));
        verify(bookings, never()).saveAllAndFlush(any());
    }

    @Test
    void checkout_sameGuideFarApart_noReservedOverlap_succeeds() {
        UserEntity participant = user(UUID.randomUUID(), "Pat");
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // Same guide + offering but a week apart: the reserved windows do NOT overlap, so the
        // guideClash isBefore/isAfter conditions evaluate false in both loop directions and
        // checkout proceeds.
        BookingEntity i1 = draftItem(participant.getId(), start, 60);
        BookingEntity i2 = draftItem(participant.getId(), start.plus(7, ChronoUnit.DAYS), 60);
        i2.setGuideId(i1.getGuideId());
        i2.setTourOfferingId(i1.getTourOfferingId());
        when(bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                        participant.getId(), BookingStatus.DRAFT))
                .thenReturn(List.of(i1, i2));
        UUID gu = stubCheckoutLookups(i1, TourStatus.ACTIVE);
        when(users.findById(gu)).thenReturn(Optional.of(user(gu, "G")));
        stubNoOverlaps();
        when(bookings.saveAllAndFlush(any())).thenReturn(List.of(i1, i2));

        List<BookingDetailResponse> resp = service().checkout(participant);

        assertEquals(2, resp.size());
        verify(bookings).saveAllAndFlush(any());
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

        // Default: the scheduled interval IS covered by availability (CTL-54 Task 6) — lenient
        // because several tests fail earlier (own-tour, blank/malformed start, notice/advance
        // window) and never reach this check.
        lenient()
                .when(availabilityOccurrences.existsContaining(eq(guideProfileId), any(), any()))
                .thenReturn(true);
        // Default: no settings row for this guide -- falls back to the schema defaults (24h
        // notice, 30d advance, 0/15-min buffers), matching the pre-fix hardcoded behavior. Tests
        // that want CUSTOM settings override this with an explicit (non-lenient) stub.
        lenient().when(settings.findByGuideId(guideProfileId)).thenReturn(Optional.empty());

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

    /** An unsaved DRAFT cart item owned by the participant (random offering/guide/university). */
    private static BookingEntity draftItem(UUID participantUserId, Instant start, int minutes) {
        BookingEntity b =
                booking(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BookingStatus.DRAFT,
                        start,
                        start.plus(minutes, ChronoUnit.MINUTES));
        b.setParticipantUserId(participantUserId);
        b.setBookingNumber("BK-" + b.getId().toString().substring(0, 8).toUpperCase());
        b.setReservedStartAt(start);
        b.setReservedEndAt(start.plus(minutes + 15, ChronoUnit.MINUTES));
        return b;
    }

    /**
     * Stubs the offering lookup (in the given status, wired to the item's guide/university ids)
     * plus, for ACTIVE offerings, an APPROVED guide and an ACTIVE university. Returns the guide's
     * user id so happy-path tests can stub the display-name lookup.
     */
    private UUID stubCheckoutLookups(BookingEntity b, TourStatus offeringStatus) {
        return stubCheckoutLookups(b, offeringStatus, 60, 5000L);
    }

    /** Same, with an explicit current duration/price (checkout re-snapshots these). */
    private UUID stubCheckoutLookups(
            BookingEntity b, TourStatus offeringStatus, int durationMin, long priceCents) {
        TourOfferingEntity o = offering(b.getTourOfferingId(), "Campus Walk");
        o.setStatus(offeringStatus);
        o.setGuideId(b.getGuideId());
        o.setUniversityId(b.getUniversityId());
        o.setDurationMin(durationMin);
        o.setPriceCents(priceCents);
        when(offerings.findById(b.getTourOfferingId())).thenReturn(Optional.of(o));
        UUID guideUserId = UUID.randomUUID();
        if (offeringStatus == TourStatus.ACTIVE) {
            GuideProfileEntity g = guideProfile(b.getGuideId(), guideUserId);
            g.setApplicationStatus(GuideApplicationStatus.APPROVED);
            when(guides.findById(b.getGuideId())).thenReturn(Optional.of(g));
            UniversityEntity u = university(b.getUniversityId(), "Test University");
            u.setStatus(UniversityStatus.ACTIVE);
            when(universities.findById(b.getUniversityId())).thenReturn(Optional.of(u));
            // Default: still within availability at checkout re-validation time (CTL-54 Task 6) —
            // lenient because some tests reject the item earlier (stale/notice-window, offering no
            // longer ACTIVE) and never reach this re-check.
            lenient()
                    .when(
                            availabilityOccurrences.existsContaining(
                                    eq(b.getGuideId()), any(), any()))
                    .thenReturn(true);
            // Default: no settings row for this guide -- schema defaults, same as
            // stubBookableOffering(); tests wanting custom settings override this explicitly.
            lenient().when(settings.findByGuideId(b.getGuideId())).thenReturn(Optional.empty());
        }
        return guideUserId;
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
