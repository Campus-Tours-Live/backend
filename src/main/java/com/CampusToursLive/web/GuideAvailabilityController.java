package com.CampusToursLive.web;

import com.CampusToursLive.domain.availability.GuideAvailabilityService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilitySummaryResponse;
import com.CampusToursLive.web.dto.BookingSettingsResponse;
import com.CampusToursLive.web.dto.CreateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.CreateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.UpdateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateBookingSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guide availability management (BFF maps /v1/guide/availability → here). Recurring weekly rules,
 * date-specific exceptions, and booking policy settings.
 */
@RestController
@RequestMapping("/guide/availability")
@Tag(
        name = "Guide availability",
        description =
                "Guide-side configuration of bookable time slots: recurring weekly hours,"
                        + " date-specific exceptions, and booking policy. Every operation requires"
                        + " the GUIDE role.")
public class GuideAvailabilityController {

    private final CurrentUser currentUser;
    private final GuideAvailabilityService availability;

    public GuideAvailabilityController(
            CurrentUser currentUser, GuideAvailabilityService availability) {
        this.currentUser = currentUser;
        this.availability = availability;
    }

    @Operation(
            summary = "Get availability summary",
            description =
                    "Returns the guide's recurring rules, date-specific exceptions, and booking"
                            + " settings in one payload.")
    @ApiResponse(
            responseCode = "200",
            description = "Rules, exceptions, and booking settings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_SUMMARY)))
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
    public ApiEnvelope<AvailabilitySummaryResponse> getSummary() {
        return ApiEnvelope.of(availability.getSummary(currentUser.requireRole(UserRole.GUIDE)));
    }

    @Operation(
            summary = "Create a recurring rule",
            description =
                    "Adds a weekly availability block. Times are wall-clock in the guide's"
                            + " timezone; overlapping blocks on the same weekday are rejected.")
    @ApiResponse(
            responseCode = "200",
            description = "The created rule.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_RULE)))
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
            description = "Invalid or overlapping rule fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/rules")
    public ApiEnvelope<AvailabilityRuleResponse> createRule(
            @RequestBody CreateAvailabilityRuleRequest req) {
        return ApiEnvelope.of(
                availability.createRule(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @Operation(
            summary = "Update a recurring rule",
            description =
                    "Partially updates an existing weekly availability block owned by the guide.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated rule.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_RULE)))
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
            responseCode = "404",
            description = "No rule with that id belongs to the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "Invalid or overlapping rule fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/rules/{ruleId}")
    public ApiEnvelope<AvailabilityRuleResponse> updateRule(
            @Parameter(description = "Id (UUID) of the availability rule.") @PathVariable
                    UUID ruleId,
            @RequestBody UpdateAvailabilityRuleRequest req) {
        return ApiEnvelope.of(
                availability.updateRule(currentUser.requireRole(UserRole.GUIDE), ruleId, req));
    }

    @Operation(
            summary = "Delete a recurring rule",
            description = "Removes a weekly availability block owned by the guide.")
    @ApiResponse(
            responseCode = "200",
            description = "Rule deleted; envelope data is null.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ENVELOPE_NULL)))
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
            responseCode = "404",
            description = "No rule with that id belongs to the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @DeleteMapping("/rules/{ruleId}")
    public ApiEnvelope<Void> deleteRule(
            @Parameter(description = "Id (UUID) of the availability rule.") @PathVariable
                    UUID ruleId) {
        availability.deleteRule(currentUser.requireRole(UserRole.GUIDE), ruleId);
        return ApiEnvelope.of(null);
    }

    @Operation(
            summary = "Create a date-specific exception",
            description =
                    "Adds a one-off override to weekly availability (all-day unavailable, time"
                            + " range, or extra hours).")
    @ApiResponse(
            responseCode = "200",
            description = "The created exception.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_EXCEPTION)))
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
            description = "Invalid exception fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/exceptions")
    public ApiEnvelope<AvailabilityExceptionResponse> createException(
            @RequestBody CreateAvailabilityExceptionRequest req) {
        return ApiEnvelope.of(
                availability.createException(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @Operation(
            summary = "Update a date-specific exception",
            description =
                    "Partially updates an existing availability exception owned by the guide.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated exception.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_EXCEPTION)))
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
            responseCode = "404",
            description = "No exception with that id belongs to the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "Invalid exception fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/exceptions/{exceptionId}")
    public ApiEnvelope<AvailabilityExceptionResponse> updateException(
            @Parameter(description = "Id (UUID) of the availability exception.") @PathVariable
                    UUID exceptionId,
            @RequestBody UpdateAvailabilityExceptionRequest req) {
        return ApiEnvelope.of(
                availability.updateException(
                        currentUser.requireRole(UserRole.GUIDE), exceptionId, req));
    }

    @Operation(
            summary = "Delete a date-specific exception",
            description = "Removes a date-specific availability override owned by the guide.")
    @ApiResponse(
            responseCode = "200",
            description = "Exception deleted; envelope data is null.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ENVELOPE_NULL)))
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
            responseCode = "404",
            description = "No exception with that id belongs to the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @DeleteMapping("/exceptions/{exceptionId}")
    public ApiEnvelope<Void> deleteException(
            @Parameter(description = "Id (UUID) of the availability exception.") @PathVariable
                    UUID exceptionId) {
        availability.deleteException(currentUser.requireRole(UserRole.GUIDE), exceptionId);
        return ApiEnvelope.of(null);
    }

    @Operation(
            summary = "Update booking settings",
            description =
                    "Partially updates per-guide booking policy (notice window, buffers,"
                            + " durations offered, acceptance mode, timezone).")
    @ApiResponse(
            responseCode = "200",
            description = "The updated booking settings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.BOOKING_SETTINGS)))
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
            description = "Invalid booking settings fields.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/booking-settings")
    public ApiEnvelope<BookingSettingsResponse> updateBookingSettings(
            @RequestBody UpdateBookingSettingsRequest req) {
        return ApiEnvelope.of(
                availability.updateBookingSettings(currentUser.requireRole(UserRole.GUIDE), req));
    }
}
