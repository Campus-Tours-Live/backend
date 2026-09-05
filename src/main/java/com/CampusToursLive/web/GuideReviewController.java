package com.CampusToursLive.web;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideReviewResponseRequest;
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
 * Guide-side review endpoints (Core: /reviews/{reviewId}/response). A guide replies publicly to a
 * review of their own tour. Requires the GUIDE role; ownership (the review's guide is the caller)
 * is enforced in the service — a non-owning guide gets 404, never a state leak.
 */
@RestController
@RequestMapping("/reviews")
@Tag(
        name = "Guide reviews",
        description =
                "A guide's public response to a review of their tour. Requires a valid platform JWT"
                        + " and the GUIDE role; only the review's own guide may respond.")
public class GuideReviewController {

    private final CurrentUser currentUser;
    private final ReviewService reviewService;

    public GuideReviewController(CurrentUser currentUser, ReviewService reviewService) {
        this.currentUser = currentUser;
        this.reviewService = reviewService;
    }

    /** Set or replace the guide's response to one of their reviews. */
    @Operation(
            summary = "Respond to a review",
            description =
                    "Sets (or replaces) the guide's public response to a review of their own tour."
                            + " response is required, non-blank, and at most 1000 characters. Only"
                            + " the review's own guide may respond; anyone else gets 404.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated review, including the guide's response.",
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
            description = "Caller does not hold the GUIDE role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Review not found, or not on one of the caller's tours.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "The response text is blank or too long.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{reviewId}/response")
    public ApiEnvelope<ReviewResponse> respond(
            @PathVariable UUID reviewId, @RequestBody GuideReviewResponseRequest req) {
        var guide = currentUser.requireGuide();
        return ApiEnvelope.of(reviewService.respondToReview(guide.profile().id(), reviewId, req));
    }
}
