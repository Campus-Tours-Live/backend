package com.CampusToursLive.web;

import com.CampusToursLive.domain.saved.SavedTourService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.SavedTourResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Participant saved tours (BFF maps /v1/participant/saved-tours → here). */
@RestController
@RequestMapping("/participant/saved-tours")
@Tag(name = "Saved tours", description = "The caller's saved tours. Requires PARTICIPANT.")
public class SavedTourController {

    private final CurrentUser currentUser;
    private final SavedTourService savedTourService;

    public SavedTourController(CurrentUser currentUser, SavedTourService savedTourService) {
        this.currentUser = currentUser;
        this.savedTourService = savedTourService;
    }

    @Operation(
            summary = "List saved tours",
            description = "Newest first; tours no longer bookable are left out.")
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
            description = "Ids only, newest first, for marking catalog cards.")
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

    @Operation(
            summary = "Save a tour",
            description = "Idempotent; re-saving returns newlySaved=false.")
    @ApiResponse(
            responseCode = "200",
            description = "The tour is saved (newly, or it already was).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.SAVED_TOUR_SAVED)))
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
            description = "Tour not found (unknown offering, or not currently bookable).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @PutMapping("/{tourOfferingId}")
    public ApiEnvelope<SavedTourResponse> save(
            @Parameter(description = "Id of the tour offering to save (UUID).") @PathVariable
                    UUID tourOfferingId) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        boolean newlySaved = savedTourService.save(user.getId(), tourOfferingId);
        return ApiEnvelope.of(new SavedTourResponse(tourOfferingId.toString(), newlySaved));
    }

    @Operation(
            summary = "Unsave a tour",
            description = "Idempotent; unsaving a tour that is not saved also returns 204.")
    @ApiResponse(responseCode = "204", description = "The tour is not saved (anymore).")
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
    @DeleteMapping("/{tourOfferingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(
            @Parameter(description = "Id of the tour offering to unsave (UUID).") @PathVariable
                    UUID tourOfferingId) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        savedTourService.unsave(user.getId(), tourOfferingId);
    }
}
