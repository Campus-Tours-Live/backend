package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CartItemResponse;
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

    private static CartItemResponse mockCartItem(String cartStatus) {
        return new CartItemResponse(
                UUID.randomUUID().toString(),
                "DRAFT",
                Instant.now().toString(),
                UUID.randomUUID().toString(),
                "Campus Walk",
                "Jane Guide",
                "Test University",
                60,
                5000L,
                5000L,
                "USD",
                cartStatus);
    }

    @Test
    void getCart_requiresParticipantRole_andWrapsListInEnvelope() {
        UserEntity u = participantUser();
        List<CartItemResponse> cart = List.of(mockCartItem("AVAILABLE"));
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
        CartItemResponse detail = mockCartItem("AVAILABLE");
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.addCartItem(u, req)).thenReturn(detail);

        assertSame(detail, controller().addItem(req).data());
    }

    @Test
    void removeItem_requiresParticipantRole_andReturnsRemainingCart() {
        UserEntity u = participantUser();
        UUID itemId = UUID.randomUUID();
        List<CartItemResponse> remaining = List.of();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.removeCartItem(u, itemId)).thenReturn(remaining);

        assertSame(remaining, controller().removeItem(itemId).data());
    }

    // CTL-97 Task 6: requireRole(PARTICIPANT) is gated by requireProvisioned() — pending -> coded
    // 404 (never a bare 401, I10), provisioned non-holder -> coded 403. GET /cart stands in for
    // the whole controller, since every endpoint delegates to the same requireRole call.

    @Test
    void getCart_404_withCode_whenPendingCaller() {
        when(currentUser.requireRole(UserRole.PARTICIPANT))
                .thenThrow(
                        new NotFoundException(
                                "Account not provisioned", "ACCOUNT_NOT_PROVISIONED"));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> controller().getCart());
        assertEquals("ACCOUNT_NOT_PROVISIONED", ex.code());
    }

    @Test
    void getCart_403_withCode_whenProvisionedNonHolder() {
        when(currentUser.requireRole(UserRole.PARTICIPANT))
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: PARTICIPANT", "ROLE_REQUIRED"));

        ForbiddenException ex =
                assertThrows(ForbiddenException.class, () -> controller().getCart());
        assertEquals("ROLE_REQUIRED", ex.code());
    }

    @Test
    void clear_requiresParticipantRole_andReturnsEmptyCart() {
        UserEntity u = participantUser();
        List<CartItemResponse> empty = List.of();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(bookingService.clearCart(u)).thenReturn(empty);

        assertSame(empty, controller().clear().data());
        verify(bookingService).clearCart(u);
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
