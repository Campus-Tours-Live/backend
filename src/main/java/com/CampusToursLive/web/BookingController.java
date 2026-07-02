package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant booking read endpoints (BFF maps /v1/participant/bookings/* → here). All endpoints
 * require the PARTICIPANT role.
 */
@RestController
@RequestMapping("/participant/bookings")
public class BookingController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;

    public BookingController(CurrentUser currentUser, BookingService bookingService) {
        this.currentUser = currentUser;
        this.bookingService = bookingService;
    }

    /** The soonest upcoming CONFIRMED booking. Returns {@code null} data if none exists. */
    @GetMapping("/next-tour")
    public ApiEnvelope<BookingDetailResponse> getNextTour() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getNextTour(user.getId()).orElse(null));
    }

    /** All upcoming active-lifecycle bookings, ordered chronologically. */
    @GetMapping("/upcoming")
    public ApiEnvelope<List<BookingDetailResponse>> getUpcoming() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getUpcomingBookings(user.getId()));
    }

    /** Counts of outstanding actions (payments, guide responses, reviews). */
    @GetMapping("/pending-actions")
    public ApiEnvelope<PendingActionsResponse> getPendingActions() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getPendingActions(user.getId()));
    }
}
