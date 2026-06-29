package com.CampusToursLive.domain.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.BookingDetailResponse;
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

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookings;
    @Mock TourOfferingRepository offerings;
    @Mock GuideProfileRepository guides;
    @Mock UserRepository users;
    @Mock UniversityRepository universities;

    private BookingService service() {
        return new BookingService(bookings, offerings, guides, users, universities);
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
