package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant booking cart (BFF maps /v1/participant/cart/* → here). Cart items are DRAFT bookings
 * — validated and priced, but holding no slot until checkout submits them all atomically. All
 * endpoints require the PARTICIPANT role.
 */
@RestController
@RequestMapping("/participant/cart")
public class CartController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;

    public CartController(CurrentUser currentUser, BookingService bookingService) {
        this.currentUser = currentUser;
        this.bookingService = bookingService;
    }

    /** The current cart, oldest item first. */
    @GetMapping
    public ApiEnvelope<List<BookingDetailResponse>> getCart() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getCart(user.getId()));
    }

    /** Validate and add one item (same body as a direct booking create). */
    @PostMapping("/items")
    public ApiEnvelope<BookingDetailResponse> addItem(@RequestBody CreateBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.addCartItem(user, req));
    }

    /** Remove one item; returns the remaining cart. */
    @DeleteMapping("/items/{id}")
    public ApiEnvelope<List<BookingDetailResponse>> removeItem(@PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.removeCartItem(user, id));
    }

    /** Submit every cart item atomically — all become PENDING_GUIDE_ACCEPTANCE, or none do. */
    @PostMapping("/checkout")
    public ApiEnvelope<List<BookingDetailResponse>> checkout() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.checkout(user));
    }
}
