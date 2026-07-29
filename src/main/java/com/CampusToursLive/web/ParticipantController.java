package com.CampusToursLive.web;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.OidcIdentity;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.security.RoleAccountContext;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Participant profile endpoints (BFF maps /v1/participant/profile → here). */
@RestController
@RequestMapping("/participant")
@Tag(
        name = "Participant profile",
        description =
                "Participant profile. Reading and editing both require the caller to already hold"
                        + " the PARTICIPANT role — acquiring PARTICIPANT happens via POST"
                        + " /v1/users/me/roles/participant (onboarding), not here.")
public class ParticipantController {

    private final CurrentUser currentUser;
    private final ParticipantService participantService;
    private final UserRepository users;

    public ParticipantController(
            CurrentUser currentUser, ParticipantService participantService, UserRepository users) {
        this.currentUser = currentUser;
        this.participantService = participantService;
        this.users = users;
    }

    @Operation(
            summary = "Get own participant profile",
            description =
                    "Returns the current user's participant profile. Requires the caller to already"
                            + " hold the PARTICIPANT role — see the coded error responses below for"
                            + " every other case.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's participant profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.PARTICIPANT_PROFILE)))
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
                    "The account is provisioned but does not hold the PARTICIPANT role"
                            + " (ROLE_REQUIRED).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Data-integrity violation: the account holds PARTICIPANT but its participant"
                            + " profile is missing (ROLE_PROFILE_STATE_INVALID).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @GetMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> getProfile() {
        RoleAccountContext.Participant context = currentUser.requireParticipant();
        // requireParticipant() already asserted PARTICIPANT held + exactly-one-profile pairing;
        // ParticipantProfileResponse also carries preferredLanguage/timezone, which live on
        // UserEntity/users, not on the ProvisionedAccount snapshot — so the managed entity is
        // still loaded here and delegated to the existing, unchanged getProfile(UserEntity).
        return ApiEnvelope.of(participantService.getProfile(loadManagedUser(context.account())));
    }

    @Operation(
            summary = "Update own participant profile",
            description =
                    "Partially updates the current user's participant profile. All fields are"
                            + " optional. Requires the caller to already hold the PARTICIPANT role"
                            + " — this endpoint is EDIT-ONLY and never grants a role. To acquire"
                            + " PARTICIPANT, use POST /v1/users/me/roles/participant instead. Fails"
                            + " 409 ROLE_NOT_ELIGIBLE if participantType=PARENT is set while the"
                            + " caller already holds GUIDE (the same bidirectional GUIDE/PARENT"
                            + " exclusion POST /v1/users/me/roles/participant enforces).")
    @ApiResponse(
            responseCode = "200",
            description = "The updated participant profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.PARTICIPANT_PROFILE)))
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
                    "The account is provisioned but does not hold the PARTICIPANT role"
                            + " (ROLE_REQUIRED). Acquire the role first via POST"
                            + " /v1/users/me/roles/participant.",
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
                    "ROLE_NOT_ELIGIBLE: participantType=PARENT was set while the caller already"
                            + " holds GUIDE (bidirectional GUIDE/PARENT exclusion).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    ApiExamples
                                                            .PROBLEM_409_ROLE_NOT_ELIGIBLE_PARTICIPANT)))
    @PatchMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt, @RequestBody ParticipantProfileUpdateRequest req) {
        RoleAccountContext.Participant context = currentUser.requireParticipant();
        UserEntity managed = loadManagedUser(context.account());
        // I14: pass the already-validated JWT's OidcIdentity straight through (Core-A resolve-once
        // — never re-resolved/re-queried in the service) so a participant_type change can be
        // serialized under the per-identity advisory lock.
        OidcIdentity oidcIdentity = new OidcIdentity(jwt.getIssuer().toString(), jwt.getSubject());
        return ApiEnvelope.of(participantService.updateProfile(managed, oidcIdentity, req));
    }

    /**
     * PATCH is EDIT-ONLY: the caller must already hold PARTICIPANT (gated via {@link
     * CurrentUser#requireParticipant()} — a provisioned non-holder gets 403 {@code ROLE_REQUIRED},
     * a pending identity gets 404 {@code ACCOUNT_NOT_PROVISIONED}). Account/role CREATION now lives
     * exclusively in {@code OnboardingService} (via {@code POST /v1/users/me/roles/participant}),
     * which calls the SAME {@link ParticipantService#updateProfile}. Unlike guide, participant's
     * {@code updateProfile} has no submit/finalize branch to guard against — {@link
     * com.CampusToursLive.domain.user.RoleGrantService#grant} is idempotent, so the redundant grant
     * call it still makes here (the caller already holds PARTICIPANT, per the gate above) is a
     * harmless no-op with no reset side effect. Re-loads the MANAGED {@link UserEntity} by {@code
     * account.userId()} — never the read-only {@link ProvisionedAccount} snapshot itself, which
     * cannot be saved back.
     */
    private UserEntity loadManagedUser(ProvisionedAccount account) {
        return users.findById(account.userId()).orElseThrow(ConflictException::accountStateInvalid);
    }
}
