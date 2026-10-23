package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideDecisionService;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideDecisionRequest;
import com.CampusToursLive.web.dto.GuideDecisionResponse;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin approve / reject of a pending guide (BFF maps /v1/admin/guides → here). */
@RestController
@RequestMapping("/admin/guides")
@Tag(name = "Admin guides", description = "Admin review of guide applications. Requires ADMIN.")
public class AdminGuideController {

    private final CurrentUser currentUser;
    private final GuideDecisionService decisions;

    public AdminGuideController(CurrentUser currentUser, GuideDecisionService decisions) {
        this.currentUser = currentUser;
        this.decisions = decisions;
    }

    @Operation(
            summary = "Decide a guide application",
            description = "Approve or reject a PENDING guide. Requires ADMIN.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's status after the decision.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_DECISION)))
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
            description = "No guide profile for that user id.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "409",
            description = "The application is not PENDING.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.PROBLEM_409_GUIDE_NOT_PENDING)))
    @ApiResponse(
            responseCode = "422",
            description = "Missing decision, reject without a reason, or reason too long.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{userId}/decision")
    public ApiEnvelope<GuideDecisionResponse> decide(
            @Parameter(description = "User id of the guide to decide.") @PathVariable UUID userId,
            @Valid @RequestBody GuideDecisionRequest req) {
        var admin = currentUser.requireRole(UserRole.ADMIN);
        GuideStatus status = decisions.decide(admin.getId(), userId, req.decision(), req.reason());
        return ApiEnvelope.of(new GuideDecisionResponse(userId.toString(), status.name()));
    }
}
