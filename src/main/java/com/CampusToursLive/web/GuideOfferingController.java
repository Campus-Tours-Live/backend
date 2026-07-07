package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourOfferingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.TourOfferingResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Guide supply-side: tour offerings (BFF maps /v1/guide/offerings → here). Every action requires
 * the GUIDE role (authorization reads user_roles, never the active role). Going live additionally
 * requires an APPROVED application — enforced in the service.
 */
@RestController
@RequestMapping("/guide/offerings")
@Tag(
        name = "Guide offerings",
        description =
                "Guide supply-side management of tour offerings. Every operation requires the GUIDE"
                        + " role; activating additionally requires an APPROVED application.")
public class GuideOfferingController {

    private final CurrentUser currentUser;
    private final TourOfferingService offerings;

    public GuideOfferingController(CurrentUser currentUser, TourOfferingService offerings) {
        this.currentUser = currentUser;
        this.offerings = offerings;
    }

    @Operation(
            summary = "List own offerings",
            description = "Lists every tour offering owned by the current guide, in any status.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's offerings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_OFFERING_LIST)))
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
    public ApiEnvelope<List<TourOfferingResponse>> list() {
        return ApiEnvelope.of(offerings.listOwn(currentUser.requireRole(UserRole.GUIDE)));
    }

    @Operation(
            summary = "Create an offering",
            description =
                    "Creates a new tour offering for the current guide. The offering starts in"
                            + " DRAFT status and must be activated separately before it is public.")
    @ApiResponse(
            responseCode = "200",
            description = "The created (DRAFT) offering.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_OFFERING)))
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
            responseCode = "422",
            description = "Missing or invalid offering fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping
    public ApiEnvelope<TourOfferingResponse> create(@RequestBody CreateOfferingRequest req) {
        return ApiEnvelope.of(offerings.create(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @Operation(
            summary = "Activate an offering",
            description =
                    "Publishes a DRAFT offering (moves it to ACTIVE). Requires the guide's"
                            + " application to be APPROVED; otherwise the service rejects it.")
    @ApiResponse(
            responseCode = "200",
            description = "The activated (ACTIVE) offering.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_OFFERING)))
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
            description =
                    "Caller does not hold the GUIDE role, or the application is not APPROVED.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "No offering with that id belongs to the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @PostMapping("/{id}/activate")
    public ApiEnvelope<TourOfferingResponse> activate(
            @Parameter(description = "Id (UUID) of the offering to activate.") @PathVariable
                    UUID id) {
        return ApiEnvelope.of(offerings.activate(currentUser.requireRole(UserRole.GUIDE), id));
    }
}
