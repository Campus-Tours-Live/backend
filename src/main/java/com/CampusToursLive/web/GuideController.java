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
                "Guide application / profile. Reading and editing both require the caller to"
                        + " already hold the GUIDE role — acquiring GUIDE happens via POST"
                        + " /v1/users/me/roles/guide (onboarding), not here.")
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
                    "Partially updates the guide application. Requires the caller to already hold"
                            + " the GUIDE role — this endpoint is EDIT-ONLY and never grants a role"
                            + " or re-finalizes the application; any {@code submit} field in the"
                            + " request body is ignored. To acquire GUIDE, use POST"
                            + " /v1/users/me/roles/guide instead.")
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
            responseCode = "403",
            description =
                    "The account is provisioned but does not hold the GUIDE role (ROLE_REQUIRED)."
                            + " Acquire the role first via POST /v1/users/me/roles/guide.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "422",
            description = "Invalid field values.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Concurrent edit: another request updated this guide's university affiliation"
                            + " between the moment this one read it and the moment it wrote, so"
                            + " this write lost the optimistic-lock (@Version) check and was not"
                            + " applied. Re-read the profile with GET /v1/guide/profile and"
                            + " resubmit the change on top of the current values. This is a"
                            + " DIFFERENT condition from GET's 409 (ROLE_PROFILE_STATE_INVALID, a"
                            + " GUIDE role with no guide profile), which retrying will not fix.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @PatchMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> updateProfile(
            @RequestBody GuideProfileUpdateRequest req) {
        RoleAccountContext.Guide context = currentUser.requireGuide();
        UserEntity managed = loadManagedUser(context.account());
        return ApiEnvelope.of(guideService.updateProfile(managed, asEdit(req)));
    }

    /**
     * PATCH is EDIT-ONLY: the caller must already hold GUIDE (gated via {@link
     * CurrentUser#requireGuide()} — a provisioned non-holder gets 403 {@code ROLE_REQUIRED}, a
     * pending identity gets 404 {@code ACCOUNT_NOT_PROVISIONED}). Account/role CREATION now lives
     * exclusively in {@code OnboardingService} (via {@code POST /v1/users/me/roles/guide}), which
     * calls the SAME {@link GuideService#updateProfile} with {@code submit=true}. To keep that
     * create path working unchanged while making PATCH incapable of granting a role or re-running
     * the submit-finalize branch (which would reset {@code guide_status} back to PENDING), {@link
     * #asEdit} forces {@code submit=false} on the request regardless of what the client sent before
     * it ever reaches the service.
     */
    private UserEntity loadManagedUser(ProvisionedAccount account) {
        return users.findById(account.userId()).orElseThrow(ConflictException::accountStateInvalid);
    }

    /**
     * Returns a copy of {@code req} with {@code submit} forced to {@code false}, so PATCH can never
     * reach {@link GuideService#updateProfile}'s submit-grant branch — see {@link
     * #loadManagedUser}.
     */
    private static GuideProfileUpdateRequest asEdit(GuideProfileUpdateRequest req) {
        return new GuideProfileUpdateRequest(
                req.firstName(),
                req.lastName(),
                req.universityId(),
                req.major(),
                req.classYear(),
                req.bio(),
                req.spokenLanguages(),
                req.tourTopics(),
                req.verificationEmail(),
                false,
                req.degree(),
                req.entryYear());
    }
}
