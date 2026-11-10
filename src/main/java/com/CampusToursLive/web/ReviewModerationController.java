package com.CampusToursLive.web;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.ModerateReviewRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin review moderation (Core: /reviews/{reviewId}/moderation). Moves a review to PUBLISHED or
 * REMOVED. Requires the ADMIN role; not ownership-scoped (an admin may moderate any review).
 */
@RestController
@RequestMapping("/reviews")
@Tag(
        name = "Review moderation",
        description =
                "Admin moderation of tour reviews — publish or remove. Requires a valid platform JWT"
                        + " and the ADMIN role.")
public class ReviewModerationController {

    private final CurrentUser currentUser;
    private final ReviewService reviewService;

    public ReviewModerationController(CurrentUser currentUser, ReviewService reviewService) {
        this.currentUser = currentUser;
        this.reviewService = reviewService;
    }

    /** Move a review to PUBLISHED or REMOVED. */
    @Operation(
            summary = "Moderate a review",
            description =
                    "Sets a review's status to PUBLISHED or REMOVED (case-insensitive). Removing a"
                            + " published review drops it from the public surfaces and the guide's /"
                            + " offering's rating aggregates; publishing a pending review stamps its"
                            + " published time. Any other target status is rejected.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated review.",
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
            description = "Caller does not hold the ADMIN role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Review not found.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "The target status is missing or is not PUBLISHED / REMOVED.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{reviewId}/moderation")
    public ApiEnvelope<ReviewResponse> moderate(
            @PathVariable UUID reviewId, @RequestBody ModerateReviewRequest req) {
        currentUser.requireNonProfileRole(UserRole.ADMIN);
        return ApiEnvelope.of(reviewService.moderateReview(reviewId, req));
    }
}
