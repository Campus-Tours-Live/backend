package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.security.RoleAccountContext;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
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
    private final UserRepository users;

    public GuideController(
            CurrentUser currentUser, GuideService guideService, UserRepository users) {
        this.currentUser = currentUser;
        this.guideService = guideService;
        this.users = users;
    }

    @Operation(
            summary = "Get own guide profile",
            description =
                    "Returns the current user's guide application / profile. Requires the caller to"
                            + " already hold the GUIDE role — see the coded error responses below"
                            + " for every other case.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's guide profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_PROFILE)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid JWT principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description =
                    "No account is provisioned for this principal yet (ACCOUNT_NOT_PROVISIONED).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "403",
            description =
                    "The account is provisioned but does not hold the GUIDE role (ROLE_REQUIRED).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Data-integrity violation: the account holds GUIDE but its guide profile is"
                            + " missing (ROLE_PROFILE_STATE_INVALID).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @GetMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> getProfile() {
        RoleAccountContext.Guide context = currentUser.requireGuide();
        return ApiEnvelope.of(guideService.getProfile(context.profile()));
    }

    @Operation(
            summary = "Update own guide profile",
            description =
                    "Partially updates the guide application. When submit=true the application is"
                            + " finalized: required fields are enforced, the GUIDE role is granted,"
                            + " and status moves to PENDING.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated guide profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_PROFILE)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid JWT principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description =
                    "No account is provisioned for this principal yet (ACCOUNT_NOT_PROVISIONED).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
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
        ProvisionedAccount account = currentUser.requireProvisioned();
        return ApiEnvelope.of(guideService.updateProfile(loadManagedUser(account), req));
    }

    /**
     * PATCH keeps working for ANY provisioned caller (not just current GUIDE holders): today,
     * {@code submit=true} is ALSO the onboarding-create path (see {@link
     * GuideService#updateProfile}, which grants GUIDE on submit) — gating this endpoint behind
     * {@link CurrentUser#requireGuide()} would 403 the very first application. So PATCH uses {@link
     * CurrentUser#requireProvisioned()} and re-loads the MANAGED {@link UserEntity} by {@code
     * account.userId()} for the service call — never the read-only {@link ProvisionedAccount}
     * snapshot itself, which cannot be saved back.
     */
    private UserEntity loadManagedUser(ProvisionedAccount account) {
        return users.findById(account.userId()).orElseThrow(ConflictException::accountStateInvalid);
    }
}
