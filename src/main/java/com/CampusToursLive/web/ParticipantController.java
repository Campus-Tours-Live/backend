package com.CampusToursLive.web;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.security.CurrentUser;
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
                "Participant profile. Any authenticated user may read/update their own participant"
                        + " profile.")
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
                            + " optional.")
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
            responseCode = "422",
            description = "Invalid field values.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> updateProfile(
            @RequestBody ParticipantProfileUpdateRequest req) {
        ProvisionedAccount account = currentUser.requireProvisioned();
        return ApiEnvelope.of(participantService.updateProfile(loadManagedUser(account), req));
    }

    /**
     * PATCH keeps working for ANY provisioned caller (not just current PARTICIPANT holders): this
     * is ALSO today's onboarding-create path (see {@link ParticipantService#updateProfile}, which
     * grants PARTICIPANT once the profile is saved) — gating behind {@link
     * CurrentUser#requireParticipant()} would 403 the very first onboarding edit. So both GET's
     * managed-entity fallback and PATCH use {@link CurrentUser#requireProvisioned()} /-derived
     * account here and re-load the MANAGED {@link UserEntity} by {@code account.userId()} — never
     * the read-only {@link ProvisionedAccount} snapshot itself, which cannot be saved back.
     */
    private UserEntity loadManagedUser(ProvisionedAccount account) {
        return users.findById(account.userId()).orElseThrow(ConflictException::accountStateInvalid);
    }
}
