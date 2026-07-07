package com.CampusToursLive.web;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.security.CurrentUser;
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

    public ParticipantController(CurrentUser currentUser, ParticipantService participantService) {
        this.currentUser = currentUser;
        this.participantService = participantService;
    }

    @Operation(
            summary = "Get own participant profile",
            description =
                    "Returns the current user's participant profile. Profile-level fields are null"
                            + " before participant onboarding.")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's participant profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.PARTICIPANT_PROFILE)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> getProfile() {
        return ApiEnvelope.of(participantService.getProfile(currentUser.require()));
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
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
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
        return ApiEnvelope.of(participantService.updateProfile(currentUser.require(), req));
    }
}
