package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant booking endpoints (BFF maps /v1/participant/bookings/* → here): dashboard reads plus
 * the create/cancel writes. All endpoints require the PARTICIPANT role; ownership of a specific
 * booking is enforced in the service.
 */
@RestController
@RequestMapping("/participant/bookings")
@Tag(
        name = "Participant bookings",
        description =
                "Read endpoints for a participant's tour bookings. Every operation requires a valid"
                        + " platform JWT and the PARTICIPANT role (authorization reads user_roles).")
public class BookingController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;

    public BookingController(CurrentUser currentUser, BookingService bookingService) {
        this.currentUser = currentUser;
        this.bookingService = bookingService;
    }

    /** The soonest upcoming CONFIRMED booking. Returns {@code null} data if none exists. */
    @Operation(
            summary = "Next upcoming tour",
            description =
                    "Returns the soonest upcoming CONFIRMED booking for the current participant, or"
                            + " an envelope with null data when there is none.")
    @ApiResponse(
            responseCode = "200",
            description = "The next booking (data may be null when there is none).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = {
                                @ExampleObject(
                                        name = "hasNext",
                                        value = ApiExamples.BOOKING_DETAIL),
                                @ExampleObject(name = "none", value = ApiExamples.NEXT_TOUR_EMPTY)
                            }))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @GetMapping("/next-tour")
    public ApiEnvelope<BookingDetailResponse> getNextTour() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getNextTour(user.getId()).orElse(null));
    }

    /** All upcoming active-lifecycle bookings, ordered chronologically. */
    @Operation(
            summary = "Upcoming tours",
            description =
                    "Lists all of the participant's upcoming active-lifecycle bookings, ordered"
                            + " chronologically (soonest first).")
    @ApiResponse(
            responseCode = "200",
            description = "The participant's upcoming bookings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.BOOKING_LIST)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @GetMapping("/upcoming")
    public ApiEnvelope<List<BookingDetailResponse>> getUpcoming() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getUpcomingBookings(user.getId()));
    }

    /** Counts of outstanding actions (payments, guide responses, reviews). */
    @Operation(
            summary = "Pending action counts",
            description =
                    "Returns counts of the participant's outstanding actions — payments to finish,"
                            + " bookings waiting for a guide, and reviews to write.")
    @ApiResponse(
            responseCode = "200",
            description = "Outstanding-action counts.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.PENDING_ACTIONS)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @GetMapping("/pending-actions")
    public ApiEnvelope<PendingActionsResponse> getPendingActions() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getPendingActions(user.getId()));
    }

    /** Book a tour: creates a PENDING_GUIDE_ACCEPTANCE booking for a bookable offering. */
    @PostMapping
    public ApiEnvelope<BookingDetailResponse> create(@RequestBody CreateBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.createBooking(user, req));
    }

    /** Cancel the participant's own upcoming booking. The body (a reason) is optional. */
    @PostMapping("/{id}/cancel")
    public ApiEnvelope<BookingDetailResponse> cancel(
            @PathVariable UUID id, @RequestBody(required = false) CancelBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.cancelBooking(user, id, req));
    }
}
