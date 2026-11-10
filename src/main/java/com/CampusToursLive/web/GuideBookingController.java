package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingService.GuideBookingFilter;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.GuideBookingDetailResponse;
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
 * Guide booking inbox (BFF maps /v1/guide/bookings → here). List pending/upcoming bookings and
 * accept or decline those awaiting guide response. Every action requires the GUIDE role; ownership
 * is enforced in the service.
 */
@RestController
@RequestMapping("/guide/bookings")
@Tag(
        name = "Guide bookings",
        description =
                "Guide booking inbox: list pending and upcoming tours, accept or decline requests."
                        + " Every operation requires the GUIDE role.")
public class GuideBookingController {

    private final CurrentUser currentUser;
    private final BookingService bookings;

    public GuideBookingController(CurrentUser currentUser, BookingService bookings) {
        this.currentUser = currentUser;
        this.bookings = bookings;
    }

    @Operation(
            summary = "List guide bookings",
            description =
                    "Lists this guide's bookings. filter=pending (awaiting accept/decline),"
                            + " upcoming (CONFIRMED starting now or later), or all (default).")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's bookings for the requested filter.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_BOOKING_LIST)))
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
            description = "Caller does not hold the GUIDE role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @GetMapping
    public ApiEnvelope<List<GuideBookingDetailResponse>> list(
            @Parameter(description = "pending | upcoming | all (default all)")
                    @RequestParam(required = false)
                    String filter) {
        return ApiEnvelope.of(
                bookings.listForGuide(
                        currentUser.requireRole(UserRole.GUIDE),
                        GuideBookingFilter.fromParam(filter)));
    }

    @Operation(
            summary = "Accept a booking request",
            description =
                    "Confirms a PENDING_GUIDE_ACCEPTANCE booking owned by the current guide."
                            + " Idempotent if already CONFIRMED.")
    @ApiResponse(
            responseCode = "200",
            description = "The confirmed booking.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_BOOKING_DETAIL)))
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
            description = "Caller does not hold the GUIDE role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Booking not found for this guide.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "Booking cannot be accepted (wrong status or response window expired).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{id}/accept")
    public ApiEnvelope<GuideBookingDetailResponse> accept(@PathVariable UUID id) {
        return ApiEnvelope.of(bookings.acceptBooking(currentUser.requireRole(UserRole.GUIDE), id));
    }

    @Operation(
            summary = "Decline a booking request",
            description =
                    "Declines a PENDING_GUIDE_ACCEPTANCE booking owned by the current guide."
                            + " Idempotent if already DECLINED_BY_GUIDE. Optional reason body.")
    @ApiResponse(
            responseCode = "200",
            description = "The declined booking.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_BOOKING_DETAIL)))
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
            description = "Caller does not hold the GUIDE role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Booking not found for this guide.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "Booking cannot be declined (wrong status).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{id}/decline")
    public ApiEnvelope<GuideBookingDetailResponse> decline(
            @PathVariable UUID id, @RequestBody(required = false) CancelBookingRequest body) {
        return ApiEnvelope.of(
                bookings.declineBooking(currentUser.requireRole(UserRole.GUIDE), id, body));
    }
}
