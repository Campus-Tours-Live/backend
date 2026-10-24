package com.CampusToursLive.web;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant review endpoints (Core: /bookings/{bookingId}/review; BFF path
 * /v1/bookings/{id}/review). A participant reviews their own COMPLETED booking. All operations
 * require the PARTICIPANT role; ownership of the booking / review is enforced in the service.
 */
@RestController
@RequestMapping("/bookings/{bookingId}/review")
@Tag(
        name = "Tour reviews",
        description =
                "A participant's review of a completed booking. Every operation requires a valid"
                        + " platform JWT and the PARTICIPANT role; ownership is enforced in the"
                        + " service (a non-owner gets 404, never a state leak).")
public class ReviewController {

    private final CurrentUser currentUser;
    private final ReviewService reviewService;

    public ReviewController(CurrentUser currentUser, ReviewService reviewService) {
        this.currentUser = currentUser;
        this.reviewService = reviewService;
    }

    /** Create the caller's review of their own completed booking. */
    @Operation(
            summary = "Review a completed booking",
            description =
                    "Creates the participant's review of their own COMPLETED booking. overallRating"
                            + " is required (1-5); the four sub-ratings are optional (1-5 each)."
                            + " comment is the public text; privateFeedback is guide-and-author"
                            + " only. One review per booking. The review auto-publishes and updates"
                            + " the guide's and offering's rating aggregates.")
    @ApiResponse(
            responseCode = "200",
            description = "The created review.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.REVIEW_DETAIL)))
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
            description = "The booking already has a review.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @ApiResponse(
            responseCode = "422",
            description =
                    "A rating is out of range, overallRating is missing, or the booking is not"
                            + " completed.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping
    public ApiEnvelope<ReviewResponse> create(
            @PathVariable UUID bookingId, @RequestBody CreateReviewRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(reviewService.createReview(user, bookingId, req));
    }

    /** The caller's own review for a booking. */
    @Operation(
            summary = "Get my review for a booking",
            description =
                    "Returns the participant's own review for the booking, including their private"
                            + " feedback. 404 when the caller has no review for that booking.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's review.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.REVIEW_DETAIL)))
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
            description = "No review by the caller for this booking.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @GetMapping
    public ApiEnvelope<ReviewResponse> get(@PathVariable UUID bookingId) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(reviewService.getReviewForBooking(user, bookingId));
    }
}
