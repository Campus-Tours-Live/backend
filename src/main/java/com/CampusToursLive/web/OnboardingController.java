package com.CampusToursLive.web;

import com.CampusToursLive.domain.onboarding.OnboardingService;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onboarding COMMAND endpoints (CTL-97 Core-B Task 7): create the GUIDE or PARTICIPANT role +
 * profile for the caller in one call, delegating straight to {@link OnboardingService} (Task 6),
 * which owns the whole provision-if-absent + create-profile + grant-role + audit transaction.
 *
 * <p>Deliberately thin, and deliberately different from every other role-scoped controller in this
 * codebase: this IS the account-creation path, so a PENDING identity (no {@code users} row yet) is
 * a VALID caller here. Neither method calls {@code CurrentUser#requireProvisioned()} or any other
 * {@code require*()} gate — doing so would 404/403 the very first onboarding call. The JWT is
 * pulled straight off the security context (already validated by the resource-server filter) via
 * {@code @AuthenticationPrincipal}, and passed through unmodified; {@link OnboardingService} does
 * the account lookup/creation itself, under its own per-identity advisory lock.
 */
@RestController
@RequestMapping("/users/me/roles")
@Tag(
        name = "Onboarding",
        description =
                "Onboarding commands: create the GUIDE or PARTICIPANT role + profile for the"
                        + " caller in one call, provisioning the account first if needed. Unlike"
                        + " every other authenticated endpoint, a PENDING identity (no account row"
                        + " yet) is a valid caller — this is the account-creation path. The"
                        + " response never includes currentRole; that is bff-owned session state.")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Operation(
            summary = "Onboard as guide",
            description =
                    "Creates the GUIDE role + profile for the caller in one call, provisioning the"
                            + " account first if this is a PENDING identity's first onboarding"
                            + " call. Fails 409 ROLE_ALREADY_GRANTED if the caller already holds"
                            + " GUIDE, or 409 ROLE_NOT_ELIGIBLE for a PARENT-type participant. No"
                            + " currentRole in the response — that is bff-owned session state.")
    @ApiResponse(
            responseCode = "201",
            description = "The account snapshot right after the GUIDE role + profile were created.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ONBOARDING_GUIDE)))
    @ApiResponse(
            responseCode = "422",
            description = "Missing required fields, or invalid values.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "ROLE_ALREADY_GRANTED (the caller already holds GUIDE) or ROLE_NOT_ELIGIBLE (a"
                            + " PARENT-type participant may not become a guide).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = {
                                @ExampleObject(
                                        name = "roleAlreadyGranted",
                                        value = ApiExamples.PROBLEM_409_ROLE_ALREADY_GRANTED),
                                @ExampleObject(
                                        name = "roleNotEligible",
                                        value = ApiExamples.PROBLEM_409_ROLE_NOT_ELIGIBLE)
                            }))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/guide")
    public ApiEnvelope<OnboardingResponse> onboardGuide(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GuideOnboardingRequest req) {
        return ApiEnvelope.of(onboardingService.onboardGuide(jwt, req));
    }

    @Operation(
            summary = "Onboard as participant",
            description =
                    "Creates the PARTICIPANT role + profile for the caller in one call,"
                            + " provisioning the account first if this is a PENDING identity's"
                            + " first onboarding call. Fails 409 ROLE_ALREADY_GRANTED if the"
                            + " caller already holds PARTICIPANT, or 409 ROLE_NOT_ELIGIBLE when"
                            + " participantType=PARENT and the caller already holds GUIDE. No"
                            + " currentRole in the response — that is bff-owned session state.")
    @ApiResponse(
            responseCode = "201",
            description =
                    "The account snapshot right after the PARTICIPANT role + profile were"
                            + " created.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ONBOARDING_PARTICIPANT)))
    @ApiResponse(
            responseCode = "422",
            description = "Missing required fields, or invalid values.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "ROLE_ALREADY_GRANTED (the caller already holds PARTICIPANT) or"
                            + " ROLE_NOT_ELIGIBLE (participantType=PARENT while already holding"
                            + " GUIDE).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = {
                                @ExampleObject(
                                        name = "roleAlreadyGranted",
                                        value = ApiExamples.PROBLEM_409_ROLE_ALREADY_GRANTED),
                                @ExampleObject(
                                        name = "roleNotEligible",
                                        value = ApiExamples.PROBLEM_409_ROLE_NOT_ELIGIBLE)
                            }))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/participant")
    public ApiEnvelope<OnboardingResponse> onboardParticipant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ParticipantOnboardingRequest req) {
        return ApiEnvelope.of(onboardingService.onboardParticipant(jwt, req));
    }
}
