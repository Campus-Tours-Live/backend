package com.CampusToursLive.web;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.PublicReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read surfaces for tour reviews (Core: /guides/{id}/reviews and /offerings/{id}/reviews).
 * Genuinely public — anonymous, like the tour catalog — and served straight from PUBLISHED reviews
 * (see {@code SecurityConfig}'s {@code permitAll} GET matchers). {@code privateFeedback} is never
 * exposed here. An unknown id simply yields an empty page.
 */
@RestController
@Tag(
        name = "Public reviews",
        description =
                "Published reviews for a guide or a tour offering — anonymous, paginated, newest"
                        + " first. Private feedback is never included.")
public class ReviewReadController {

    private final ReviewService reviewService;

    public ReviewReadController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** A page of a guide's published reviews, newest first. */
    @Operation(
            summary = "List a guide's reviews",
            description =
                    "Returns a page of the guide's PUBLISHED reviews, newest first. Anonymous;"
                            + " private feedback is never included. An unknown guide id yields an"
                            + " empty page.")
    @ApiResponse(
            responseCode = "200",
            description = "A page of the guide's published reviews.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.REVIEW_LIST)))
    @GetMapping("/guides/{guideId}/reviews")
    public ApiEnvelope<PagedResponse<PublicReviewResponse>> guideReviews(
            @PathVariable UUID guideId,
            @Parameter(description = "Zero-based page index.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page,
            @Parameter(description = "Page size — max rows per page.")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit) {
        return ApiEnvelope.of(
                PagedResponse.of(reviewService.getGuideReviews(guideId, page, limit)));
    }

    /** A page of an offering's published reviews, newest first. */
    @Operation(
            summary = "List a tour offering's reviews",
            description =
                    "Returns a page of the offering's PUBLISHED reviews, newest first. Anonymous;"
                            + " private feedback is never included. An unknown offering id yields an"
                            + " empty page.")
    @ApiResponse(
            responseCode = "200",
            description = "A page of the offering's published reviews.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.REVIEW_LIST)))
    @GetMapping("/offerings/{offeringId}/reviews")
    public ApiEnvelope<PagedResponse<PublicReviewResponse>> offeringReviews(
            @PathVariable UUID offeringId,
            @Parameter(description = "Zero-based page index.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page,
            @Parameter(description = "Page size — max rows per page.")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit) {
        return ApiEnvelope.of(
                PagedResponse.of(reviewService.getOfferingReviews(offeringId, page, limit)));
    }
}
