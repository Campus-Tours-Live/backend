package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CartController — thin adapter: enforces PARTICIPANT role, delegates to BookingService, wraps the
 * result in the {@code {data, meta}} envelope. Business logic lives in BookingService.
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock CurrentUser currentUser;
    @Mock BookingService bookingService;

    private CartController controller() {
        return new CartController(currentUser, bookingService);
    }

    private static UserEntity participantUser() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static BookingDetailResponse mockDetail(String status) {
        return new BookingDetailResponse(
                UUID.randomUUID().toString(),
                status,
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

    @Test
    void getCart_requiresParticipantRole_andWrapsListInEnvelope() {
        UserEntity u = participantUser();
        List<BookingDetailResponse> cart = List.of(mockDetail("DRAFT"));
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.getCart(u.getId())).thenReturn(cart);

        assertSame(cart, controller().getCart().data());
    }

    @Test
    void addItem_requiresParticipantRole_andWrapsResultInEnvelope() {
        UserEntity u = participantUser();
        CreateBookingRequest req =
                new CreateBookingRequest(
                        UUID.randomUUID().toString(), "2026-07-10T17:00:00Z", null);
        BookingDetailResponse detail = mockDetail("DRAFT");
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.addCartItem(u, req)).thenReturn(detail);

        assertSame(detail, controller().addItem(req).data());
    }

    @Test
    void removeItem_requiresParticipantRole_andReturnsRemainingCart() {
        UserEntity u = participantUser();
        UUID itemId = UUID.randomUUID();
        List<BookingDetailResponse> remaining = List.of();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.removeCartItem(u, itemId)).thenReturn(remaining);

        assertSame(remaining, controller().removeItem(itemId).data());
    }

    @Test
    void checkout_requiresParticipantRole_andWrapsSubmittedBookings() {
        UserEntity u = participantUser();
        List<BookingDetailResponse> submitted =
                List.of(mockDetail("WAITING_FOR_GUIDE"), mockDetail("WAITING_FOR_GUIDE"));
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.checkout(u)).thenReturn(submitted);

        assertSame(submitted, controller().checkout().data());
        verify(bookingService).checkout(u);
    }
}
