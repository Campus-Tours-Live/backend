package com.CampusToursLive.web;

import com.CampusToursLive.domain.saved.SavedTourService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.TourSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A participant's saved tours (BFF maps /v1/participant/saved-tours → here). Every operation
 * requires the PARTICIPANT role and acts only on the caller's own saves.
 */
@RestController
@RequestMapping("/participant/saved-tours")
@Tag(
        name = "Saved tours",
        description =
                "A participant's saved tours (wishlist). Every operation requires a valid platform"
                        + " JWT and the PARTICIPANT role, and only ever reads or changes the"
                        + " caller's own saves.")
public class SavedTourController {

    private final CurrentUser currentUser;
    private final SavedTourService savedTourService;

    public SavedTourController(CurrentUser currentUser, SavedTourService savedTourService) {
        this.currentUser = currentUser;
        this.savedTourService = savedTourService;
    }

    @Operation(
            summary = "List saved tours",
            description =
                    "Returns the caller's saved tours as marketplace cards, most recently saved"
                            + " first. Tours that are no longer bookable on the marketplace are"
                            + " left out of the page and its totals.")
    @ApiResponse(
            responseCode = "200",
            description = "A page of the caller's saved tours.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.SAVED_TOUR_PAGE)))
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
    @GetMapping
    public ApiEnvelope<PagedResponse<TourSummaryResponse>> list(
            @Parameter(description = "Zero-based page index.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page,
            @Parameter(description = "Page size — max rows per page (capped at 50).")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(PagedResponse.of(savedTourService.list(user.getId(), page, limit)));
    }

    @Operation(
            summary = "List saved tour ids",
            description =
                    "Returns only the ids of every tour offering the caller has saved, most recent"
                            + " first, so the catalog can mark saved cards without fetching them."
                            + " Not filtered by marketplace visibility.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's saved tour offering ids.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.SAVED_TOUR_IDS)))
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
    @GetMapping("/ids")
    public ApiEnvelope<List<UUID>> ids() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(savedTourService.savedTourIds(user.getId()));
    }
}
