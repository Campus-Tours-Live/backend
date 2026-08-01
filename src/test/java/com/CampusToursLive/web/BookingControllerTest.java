package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BookingController — thin adapter: enforces PARTICIPANT role, delegates to BookingService, wraps
 * the result in the {@code {data, meta}} envelope. Business logic lives in BookingService.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock CurrentUser currentUser;
    @Mock BookingService bookingService;

    private BookingController controller() {
        return new BookingController(currentUser, bookingService);
    }

    private static UserEntity participantUser() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static BookingDetailResponse mockDetail() {
        return new BookingDetailResponse(
                UUID.randomUUID().toString(),
                "CONFIRMED",
                Instant.now().toString(),
                UUID.randomUUID().toString(),
                "Campus Walk",
                "Jane Guide",
                null,
                "Test University",
                60,
                5000L,
                "USD");
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_requiresParticipantRole_andWrapsListInEnvelope() {
        UserEntity u = participantUser();
        List<BookingDetailResponse> bookings = List.of(mockDetail(), mockDetail());
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.listBookings(u.getId(), List.of())).thenReturn(bookings);

        assertSame(bookings, controller().list(null).data());
    }

    @Test
    void list_parsesStatusFilter_andPassesToService() {
        UserEntity u = participantUser();
        List<BookingDetailResponse> bookings = List.of(mockDetail());
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.listBookings(u.getId(), List.of(BookingStatus.CONFIRMED)))
                .thenReturn(bookings);

        assertSame(bookings, controller().list(List.of("CONFIRMED")).data());
    }

    @Test
    void list_rejects_unknownStatus() {
        UserEntity u = participantUser();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);

        assertThrows(ValidationException.class, () -> controller().list(List.of("NOT_A_STATUS")));
    }

    @Test
    void list_rejects_draftStatus() {
        UserEntity u = participantUser();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);

        assertThrows(ValidationException.class, () -> controller().list(List.of("DRAFT")));
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_requiresParticipantRole_andWrapsDetailInEnvelope() {
        UserEntity u = participantUser();
        UUID bookingId = UUID.randomUUID();
        BookingDetailResponse detail = mockDetail();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getBookingById(u, bookingId)).thenReturn(detail);

        assertSame(detail, controller().get(bookingId).data());
    }

    // ── getNextTour ──────────────────────────────────────────────────────────

    @Test
    void getNextTour_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participantUser();
        BookingDetailResponse detail = mockDetail();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getNextTour(u.getId())).thenReturn(Optional.of(detail));

        assertSame(detail, controller().getNextTour().data());
    }

    @Test
    void getNextTour_returnsNullData_whenNoUpcomingTour() {
        UserEntity u = participantUser();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getNextTour(u.getId())).thenReturn(Optional.empty());

        assertNull(controller().getNextTour().data());
    }

    // ── getUpcoming ──────────────────────────────────────────────────────────

    @Test
    void getUpcoming_requiresParticipantRole_andWrapsListInEnvelope() {
        UserEntity u = participantUser();
        List<BookingDetailResponse> list = List.of(mockDetail(), mockDetail());
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getUpcomingBookings(u.getId())).thenReturn(list);

        assertSame(list, controller().getUpcoming().data());
    }

    @Test
    void getUpcoming_returnsEmptyList_whenNoUpcomingBookings() {
        UserEntity u = participantUser();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getUpcomingBookings(u.getId())).thenReturn(List.of());

        assertTrue(controller().getUpcoming().data().isEmpty());
    }

    // ── getPendingActions ────────────────────────────────────────────────────

    @Test
    void getPendingActions_requiresParticipantRole_andWrapsCountsInEnvelope() {
        UserEntity u = participantUser();
        PendingActionsResponse counts = new PendingActionsResponse(1L, 2L, 3L);
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getPendingActions(u.getId())).thenReturn(counts);

        assertSame(counts, controller().getPendingActions().data());
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participantUser();
        CreateBookingRequest req =
                new CreateBookingRequest(
                        UUID.randomUUID().toString(), "2026-07-10T17:00:00Z", null);
        BookingDetailResponse detail = mockDetail();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.createBooking(u, req)).thenReturn(detail);

        assertSame(detail, controller().create(req).data());
        verify(bookingService).createBooking(u, req);
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancel_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participantUser();
        UUID bookingId = UUID.randomUUID();
        CancelBookingRequest req = new CancelBookingRequest("plans changed");
        BookingDetailResponse detail = mockDetail();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.cancelBooking(u, bookingId, req)).thenReturn(detail);

        assertSame(detail, controller().cancel(bookingId, req).data());
    }

    @Test
    void cancel_acceptsMissingBody() {
        UserEntity u = participantUser();
        UUID bookingId = UUID.randomUUID();
        BookingDetailResponse detail = mockDetail();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.cancelBooking(u, bookingId, null)).thenReturn(detail);

        assertSame(detail, controller().cancel(bookingId, null).data());
    }
}
