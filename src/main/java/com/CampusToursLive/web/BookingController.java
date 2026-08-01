package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant booking endpoints (Core: /bookings; BFF path /v1/bookings/*): dashboard reads plus
 * the create/cancel writes. All require the PARTICIPANT role; ownership of a booking is enforced in
 * the service.
 */
@RestController
@RequestMapping("/bookings")
@Tag(
        name = "Participant bookings",
        description =
                "A participant's tour bookings: dashboard reads plus create and cancel. Every"
                        + " operation requires a valid platform JWT and the PARTICIPANT role"
                        + " (authorization reads user_roles).")
public class BookingController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;

    public BookingController(CurrentUser currentUser, BookingService bookingService) {
        this.currentUser = currentUser;
        this.bookingService = bookingService;
    }

    /** All non-DRAFT bookings for the caller, newest first, with an optional status filter. */
    @Operation(
            summary = "List my bookings",
            description =
                    "Returns the participant's booking history (all non-DRAFT bookings), newest"
                            + " first. Pass one or more status values to narrow the results; omit"
                            + " for the full history. DRAFT (cart) items are excluded — use"
                            + " GET /cart for those.")
    @ApiResponse(
            responseCode = "200",
            description = "The participant's bookings.",
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
    @ApiResponse(
            responseCode = "422",
            description = "Unknown status value.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping
    public ApiEnvelope<List<BookingDetailResponse>> list(
            @Parameter(
                            description =
                                    "Filter by internal booking status codes (repeatable). Omit for"
                                            + " all non-DRAFT bookings.")
                    @RequestParam(name = "status", required = false)
                    List<String> status) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.listBookings(user.getId(), parseStatuses(status)));
    }

    /** Fetch a single booking by id — must be owned by the caller. */
    @Operation(
            summary = "Get a booking",
            description =
                    "Returns the full detail for a single booking owned by the current participant.")
    @ApiResponse(
            responseCode = "200",
            description = "The booking detail.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.BOOKING_DETAIL)))
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
    @ApiResponse(
            responseCode = "404",
            description = "Booking not found, or not owned by the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @GetMapping("/{id}")
    public ApiEnvelope<BookingDetailResponse> get(
            @Parameter(description = "Booking id (UUID).") @PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getBookingById(user, id));
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
    @Operation(
            summary = "Book a tour",
            description =
                    "Creates a booking for a bookable offering (ACTIVE offering by an APPROVED"
                            + " guide at an ACTIVE university). The offering's price and duration"
                            + " are snapshotted onto the booking, which starts in"
                            + " PENDING_GUIDE_ACCEPTANCE with a 90-minute guide response window."
                            + " Notice/advance/buffer requirements are the guide's own booking"
                            + " settings (24h notice, 30-day advance by default); times conflicting"
                            + " with existing bookings are rejected.")
    @ApiResponse(
            responseCode = "200",
            description = "The created booking, waiting for the guide's acceptance.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.BOOKING_DETAIL)))
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
    @ApiResponse(
            responseCode = "404",
            description = "Tour not found (unknown offering, or not currently ACTIVE).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description =
                    "Validation failed — bad fields, notice/advance window violated, guide not"
                            + " bookable, or the time conflicts with an existing booking.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping
    public ApiEnvelope<BookingDetailResponse> create(@RequestBody CreateBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.createBooking(user, req));
    }

    private static List<BookingStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(
                        s -> {
                            try {
                                BookingStatus parsed =
                                        BookingStatus.valueOf(s.trim().toUpperCase());
                                if (parsed == BookingStatus.DRAFT) {
                                    throw new ValidationException(
                                            "DRAFT bookings are managed via /cart");
                                }
                                return parsed;
                            } catch (IllegalArgumentException e) {
                                throw new ValidationException("Unknown booking status: " + s);
                            }
                        })
                .toList();
    }

    /** Cancel the participant's own upcoming booking. The body (a reason) is optional. */
    @Operation(
            summary = "Cancel a booking",
            description =
                    "Cancels the participant's own upcoming booking (pending or CONFIRMED, before"
                            + " the scheduled start). Idempotent when the booking is already"
                            + " participant-cancelled; an optional reason (max 1000 characters) is"
                            + " recorded.")
    @ApiResponse(
            responseCode = "200",
            description = "The cancelled booking.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.BOOKING_CANCELLED)))
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
    @ApiResponse(
            responseCode = "404",
            description = "Booking not found, or not owned by the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "409",
            description = "The booking was modified concurrently — retry.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @ApiResponse(
            responseCode = "422",
            description = "The booking can no longer be cancelled (started or terminal status).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{id}/cancel")
    public ApiEnvelope<BookingDetailResponse> cancel(
            @PathVariable UUID id, @RequestBody(required = false) CancelBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.cancelBooking(user, id, req));
    }
}
