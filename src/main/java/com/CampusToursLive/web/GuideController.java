package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideEarningsService;
import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideDashboardStatsResponse;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guide application / profile endpoints (BFF maps /v1/guide/profile → here). */
@RestController
@RequestMapping("/guide")
@Tag(
        name = "Guide profile",
        description =
                "Guide application / profile. Any authenticated user may read/update their own"
                        + " guide profile; submitting the update grants the GUIDE role.")
public class GuideController {

    private final CurrentUser currentUser;
    private final GuideService guideService;
    private final GuideEarningsService guideEarningsService;

    public GuideController(
            CurrentUser currentUser,
            GuideService guideService,
            GuideEarningsService guideEarningsService) {
        this.currentUser = currentUser;
        this.guideService = guideService;
        this.guideEarningsService = guideEarningsService;
    }

    @Operation(
            summary = "Get own guide profile",
            description =
                    "Returns the current user's guide application / profile. Profile-level fields"
                            + " are null if guide onboarding has not started.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's guide profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_PROFILE)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> getProfile() {
        return ApiEnvelope.of(guideService.getProfile(currentUser.require()));
    }

    @Operation(
            summary = "Update own guide profile",
            description =
                    "Partially updates the guide application. When submit=true the application is"
                            + " finalized: required fields are enforced, the GUIDE role is granted,"
                            + " and status moves to PENDING_REVIEW.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated guide profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_PROFILE)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "422",
            description = "Missing required fields on submit, or invalid values.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> updateProfile(
            @RequestBody GuideProfileUpdateRequest req) {
        return ApiEnvelope.of(guideService.updateProfile(currentUser.require(), req));
    }

    @Operation(
            summary = "Get guide dashboard stats",
            description =
                    "Returns the guide's aggregate rating, this-month earnings, and upcoming payout"
                            + " from confirmed tours — the three stats shown on the guide dashboard"
                            + " summary row.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's dashboard stats snapshot.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_DASHBOARD_STATS)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description = "The caller has not started guide onboarding.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @GetMapping("/dashboard/stats")
    public ApiEnvelope<GuideDashboardStatsResponse> getDashboardStats() {
        return ApiEnvelope.of(guideEarningsService.getStats(currentUser.require()));
    }
}
