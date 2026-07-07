package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideDecisionRequest;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Staff/admin endpoints (BFF maps /v1/admin/* → here). Requires the ADMIN role — an
 * externally-granted staff role; authorization reads user_roles, independent of the caller's active
 * consumer role.
 */
@RestController
@RequestMapping("/admin")
@Tag(
        name = "Admin",
        description =
                "Staff-only endpoints. Requires the externally-granted ADMIN role (read from"
                        + " user_roles, never the active role).")
public class AdminController {

    private final CurrentUser currentUser;
    private final GuideService guideService;

    public AdminController(CurrentUser currentUser, GuideService guideService) {
        this.currentUser = currentUser;
        this.guideService = guideService;
    }

    /** Approve or reject a guide application → sets guide_profile.application_status. */
    @Operation(
            summary = "Decide a guide application",
            description =
                    "Approves or rejects a pending guide application, setting the guide's"
                            + " application_status. Returns the updated guide profile.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide profile after the decision.",
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
            responseCode = "403",
            description = "Caller does not hold the ADMIN role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "No guide application exists for the given user id.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @PostMapping("/guides/{userId}/decision")
    public ApiEnvelope<GuideProfileResponse> decide(
            @Parameter(description = "Id (UUID) of the guide whose application is being decided.")
                    @PathVariable
                    UUID userId,
            @RequestBody GuideDecisionRequest req) {
        currentUser.requireRole(UserRole.ADMIN);
        return ApiEnvelope.of(guideService.reviewApplication(userId, req.decision()));
    }
}
