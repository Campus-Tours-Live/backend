package com.CampusToursLive.web;

import com.CampusToursLive.domain.availability.AvailabilityPreviewService;
import com.CampusToursLive.domain.availability.AvailabilityReadService;
import com.CampusToursLive.domain.availability.AvailabilityWriteService;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.AffectedBookingResponse;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilityWriteResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsUpdateRequest;
import com.CampusToursLive.web.dto.OverridePreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guide-facing availability WRITE API (CTL-54 Task 5): create/update/delete recurring rules and
 * one-off exceptions, plus read/update booking settings. Every route is GENERIC — no {@code /guide}
 * prefix — the caller's role and guide id come from the security context ({@link
 * CurrentUser#requireRole(UserRole)}), never from the URL (mirrors {@code BookingController} /
 * {@code CartController}, the CTL-43 convention). Consumed via the BFF (CTL-56), not directly by
 * clients.
 *
 * <p>Every write triggers {@code AvailabilityService.rematerialize(guideId)} inside the same
 * transaction (see {@link AvailabilityWriteService}), so the materialized occurrences never lag the
 * rules/exceptions/settings a guide just edited. This does NOT include the resolved read ({@code
 * GET /availability}, CTL-54 Task 5b -- see {@link AvailabilityReadService}, a pure read that never
 * rematerializes) or booking-containment (Task 6).
 *
 * <p><b>Task 7 -- "(A) allow + notify".</b> Every write endpoint below returns {@link
 * AvailabilityWriteResponse} rather than the plain {@link ApiEnvelope}: after the edit commits (and
 * re-materializes), this controller ALSO asks {@link AvailabilityWriteService#findAffectedBookings}
 * for the guide's own future CONFIRMED bookings the edit left uncovered by any occurrence. The edit
 * still succeeds either way and the booking is NEVER mutated -- {@code affectedBookings} is purely
 * advisory so the guide-facing UI can warn. The read routes ({@code GET /availability}, {@code
 * /rules}, {@code /exceptions}, {@code /settings}) are unaffected and keep returning the plain
 * {@link ApiEnvelope}.
 */
@RestController
@RequestMapping("/availability")
@Tag(
        name = "Guide availability",
        description =
                "A guide's recurring availability rules, one-off exceptions, and booking settings."
                        + " Every operation requires the GUIDE role; every write re-materializes the"
                        + " guide's availability occurrences in the same transaction.")
public class AvailabilityController {

    private static final String NO_GUIDE_PROFILE_422 =
            " Also 422 when the caller holds the GUIDE role but has not completed guide"
                    + " onboarding (no guide profile yet).";

    private final CurrentUser currentUser;
    private final AvailabilityWriteService availability;
    private final AvailabilityReadService availabilityRead;
    private final AvailabilityPreviewService availabilityPreview;
    private final GuideProfileRepository guides;

    public AvailabilityController(
            CurrentUser currentUser,
            AvailabilityWriteService availability,
            AvailabilityReadService availabilityRead,
            AvailabilityPreviewService availabilityPreview,
            GuideProfileRepository guides) {
        this.currentUser = currentUser;
        this.availability = availability;
        this.availabilityRead = availabilityRead;
        this.availabilityPreview = availabilityPreview;
        this.guides = guides;
    }

    /**
     * The guide's resolved availability (CTL-54 Task 5b): editable rules + backend-coalesced
     * occurrences + DST gap-days -- the single source of truth CTL-55/CTL-56 render read-only
     * without re-coalescing. {@code from} / {@code to} (ISO {@code yyyy-MM-dd}) optionally narrow
     * the returned occurrences to those intersecting {@code [from, to)}; omitted, every
     * materialized occurrence is returned.
     */
    @Operation(
            summary = "Resolved availability (rules + occurrences + DST gaps)",
            description =
                    "Returns the guide's editable rules, the backend-coalesced net-available"
                            + " occurrences for the requested window, and any DST gap-moved/skipped"
                            + " days the projection reported. The frontend renders occurrences"
                            + " as-is and must not re-coalesce — this endpoint is the single source"
                            + " of truth. from/to (ISO yyyy-MM-dd) optionally narrow the returned"
                            + " occurrences to those intersecting [from, to); omitted, every"
                            + " materialized occurrence is returned.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's resolved availability.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.RESOLVED_AVAILABILITY)))
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
            description =
                    "from/to malformed (expected ISO yyyy-MM-dd), or to not after from."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping
    public ApiEnvelope<ResolvedAvailabilityResponse> getResolvedAvailability(
            @Parameter(description = "ISO yyyy-MM-dd; inclusive lower bound of the window.")
                    @RequestParam(required = false)
                    String from,
            @Parameter(description = "ISO yyyy-MM-dd; exclusive upper bound of the window.")
                    @RequestParam(required = false)
                    String to) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availabilityRead.getResolvedAvailability(user, from, to));
    }

    /**
     * Date-specific override dry-run/preview (CTL-54 v2.1 Task 4): given a proposed override that
     * has NOT been saved, returns the resulting net-available windows per date exactly as an actual
     * save would produce them, plus which existing exception segments the override would trim --
     * WITHOUT persisting anything. Owner-scoped like every other route here: the guide id is
     * resolved from the caller's own guide profile, never from the request. No springdoc yet (Task
     * 5).
     */
    @GetMapping("/preview")
    public ApiEnvelope<OverridePreviewResponse> getOverridePreview(
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam String kind,
            @RequestParam String startLocal,
            @RequestParam Integer windowMin) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        UUID guideId = requireGuideId(user);
        OverridePreviewRequest req =
                new OverridePreviewRequest(dateFrom, dateTo, kind, startLocal, windowMin);
        return ApiEnvelope.of(availabilityPreview.preview(guideId, req));
    }

    /** List the guide's recurring availability rules. */
    @Operation(
            summary = "List availability rules",
            description = "Lists every one of the guide's recurring availability rules.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's availability rules.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_RULE_LIST)))
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
            description =
                    "Caller holds the GUIDE role but has not completed guide onboarding (no guide"
                            + " profile yet).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping("/rules")
    public ApiEnvelope<List<AvailabilityRuleResponse>> listRules() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.listRules(user));
    }

    /** Create a recurring availability rule; timezone is server-set to the guide's settings tz. */
    @Operation(
            summary = "Create an availability rule",
            description =
                    "Creates a recurring availability rule (day of week + start + window). The"
                            + " rule's timezone is server-set to the guide's settings timezone —"
                            + " never taken from the request. The write re-materializes the guide's"
                            + " occurrences in the same transaction, and the response also"
                            + " surfaces (CTL-54 Task 7) any of the guide's own future CONFIRMED"
                            + " bookings this edit left uncovered by any occurrence; the edit still"
                            + " succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The created rule, plus any newly-uncovered bookings (advisory).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = {
                                @ExampleObject(
                                        name = "noWarning",
                                        value = ApiExamples.AVAILABILITY_RULE_WRITE),
                                @ExampleObject(
                                        name = "withWarning",
                                        value = ApiExamples.AVAILABILITY_RULE_WRITE_WITH_WARNING)
                            }))
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
            description =
                    "Missing/invalid fields (dayOfWeek, startLocal, windowMin, effectiveTo before"
                            + " effectiveFrom)."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/rules")
    public AvailabilityWriteResponse<AvailabilityRuleResponse> createRule(
            @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityRuleResponse created = availability.createRule(user, req);
        return AvailabilityWriteResponse.of(created, affectedBookings(user));
    }

    /** Update an owned rule (404 if the id does not belong to the caller). */
    @Operation(
            summary = "Update an availability rule",
            description =
                    "Updates an owned recurring availability rule. The rule's timezone is never"
                            + " updated from the request. The write re-materializes the guide's"
                            + " occurrences and the response surfaces (CTL-54 Task 7) any newly"
                            + " uncovered future CONFIRMED bookings; the edit still succeeds and no"
                            + " booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated rule, plus any newly-uncovered bookings (advisory).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.AVAILABILITY_RULE_WRITE)))
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
            description =
                    "Missing/invalid fields (dayOfWeek, startLocal, windowMin, effectiveTo before"
                            + " effectiveFrom)."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/rules/{id}")
    public AvailabilityWriteResponse<AvailabilityRuleResponse> updateRule(
            @Parameter(description = "Id (UUID) of the rule to update.") @PathVariable UUID id,
            @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityRuleResponse updated = availability.updateRule(user, id, req);
        return AvailabilityWriteResponse.of(updated, affectedBookings(user));
    }

    /** Delete an owned rule (404 if not owned); returns the guide's remaining rules. */
    @Operation(
            summary = "Delete an availability rule",
            description =
                    "Deletes an owned recurring availability rule and returns the guide's"
                            + " remaining rules. The write re-materializes the guide's occurrences"
                            + " and the response surfaces (CTL-54 Task 7) any newly uncovered"
                            + " future CONFIRMED bookings; the deletion still succeeds and no"
                            + " booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's remaining rules, plus any newly-uncovered bookings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.AVAILABILITY_RULE_LIST_WRITE)))
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
    @DeleteMapping("/rules/{id}")
    public AvailabilityWriteResponse<List<AvailabilityRuleResponse>> deleteRule(
            @Parameter(description = "Id (UUID) of the rule to delete.") @PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        List<AvailabilityRuleResponse> remaining = availability.deleteRule(user, id);
        return AvailabilityWriteResponse.of(remaining, affectedBookings(user));
    }

    /** List the guide's one-off availability exceptions. */
    @Operation(
            summary = "List availability exceptions",
            description = "Lists every one of the guide's one-off availability exceptions.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's availability exceptions.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.AVAILABILITY_EXCEPTION_LIST)))
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
    @GetMapping("/exceptions")
    public ApiEnvelope<List<AvailabilityExceptionResponse>> listExceptions() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.listExceptions(user));
    }

    /** Create a one-off availability exception (UNAVAILABLE or ADDITIONAL). */
    @Operation(
            summary = "Create an availability exception",
            description =
                    "Creates a one-off availability exception (UNAVAILABLE removes availability,"
                            + " ADDITIONAL adds it) for a specific date. The write re-materializes"
                            + " the guide's occurrences and the response surfaces (CTL-54 Task 7)"
                            + " any newly uncovered future CONFIRMED bookings; the edit still"
                            + " succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The created exception, plus any newly-uncovered bookings (advisory).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.AVAILABILITY_EXCEPTION_WRITE)))
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
            description =
                    "Missing/invalid fields (exceptionDate, kind, startLocal, windowMin)."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/exceptions")
    public AvailabilityWriteResponse<AvailabilityExceptionResponse> createException(
            @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityExceptionResponse created = availability.createException(user, req);
        return AvailabilityWriteResponse.of(created, affectedBookings(user));
    }

    /** Update an owned exception (404 if the id does not belong to the caller). */
    @Operation(
            summary = "Update an availability exception",
            description =
                    "Updates an owned one-off availability exception. The write re-materializes"
                            + " the guide's occurrences and the response surfaces (CTL-54 Task 7)"
                            + " any newly uncovered future CONFIRMED bookings; the edit still"
                            + " succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated exception, plus any newly-uncovered bookings (advisory).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.AVAILABILITY_EXCEPTION_WRITE)))
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
            description =
                    "Missing/invalid fields (exceptionDate, kind, startLocal, windowMin)."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/exceptions/{id}")
    public AvailabilityWriteResponse<AvailabilityExceptionResponse> updateException(
            @Parameter(description = "Id (UUID) of the exception to update.") @PathVariable UUID id,
            @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityExceptionResponse updated = availability.updateException(user, id, req);
        return AvailabilityWriteResponse.of(updated, affectedBookings(user));
    }

    /** Delete an owned exception (404 if not owned); returns the guide's remaining exceptions. */
    @Operation(
            summary = "Delete an availability exception",
            description =
                    "Deletes an owned one-off availability exception and returns the guide's"
                            + " remaining exceptions. The write re-materializes the guide's"
                            + " occurrences and the response surfaces (CTL-54 Task 7) any newly"
                            + " uncovered future CONFIRMED bookings; the deletion still succeeds"
                            + " and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's remaining exceptions, plus any newly-uncovered bookings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.AVAILABILITY_EXCEPTION_LIST_WRITE)))
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
    @DeleteMapping("/exceptions/{id}")
    public AvailabilityWriteResponse<List<AvailabilityExceptionResponse>> deleteException(
            @Parameter(description = "Id (UUID) of the exception to delete.") @PathVariable
                    UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        List<AvailabilityExceptionResponse> remaining = availability.deleteException(user, id);
        return AvailabilityWriteResponse.of(remaining, affectedBookings(user));
    }

    /** The guide's booking settings; auto-provisions a default row the first time it is asked. */
    @Operation(
            summary = "Get booking settings",
            description =
                    "Returns the guide's booking settings (acceptance mode, notice/advance"
                            + " windows, buffers, offered durations, timezone). Auto-provisions"
                            + " a default settings row the first time a guide is asked, so a"
                            + " guide always has one.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's booking settings.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.GUIDE_BOOKING_SETTINGS)))
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
            description =
                    "Caller holds the GUIDE role but has not completed guide onboarding (no guide"
                            + " profile yet).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping("/settings")
    public ApiEnvelope<GuideBookingSettingsResponse> getSettings() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.getSettings(user));
    }

    /**
     * Update the guide's booking settings. Changing {@code timezone} cascades the new zone onto
     * every existing rule (the read-only-tz invariant) before re-materializing.
     */
    @Operation(
            summary = "Update booking settings",
            description =
                    "Partially updates the guide's booking settings — a null/omitted field leaves"
                            + " that setting unchanged. Changing timezone cascades the new zone"
                            + " onto every one of the guide's existing rules (the read-only-tz"
                            + " invariant) before re-materializing. The response surfaces (CTL-54"
                            + " Task 7) any newly uncovered future CONFIRMED bookings; the update"
                            + " still succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated settings, plus any newly-uncovered bookings (advisory).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.GUIDE_BOOKING_SETTINGS_WRITE)))
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
            description =
                    "Invalid field value (acceptanceMode, responseDeadlineMin, minNoticeMin,"
                            + " maxAdvanceDays, buffers, durationsOffered, or timezone)."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PatchMapping("/settings")
    public AvailabilityWriteResponse<GuideBookingSettingsResponse> updateSettings(
            @RequestBody GuideBookingSettingsUpdateRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        GuideBookingSettingsResponse updated = availability.updateSettings(user, req);
        return AvailabilityWriteResponse.of(updated, affectedBookings(user));
    }

    /**
     * CTL-54 Task 7: the caller's own future CONFIRMED bookings left uncovered by any current
     * occurrence, computed AFTER the write above has already committed its rematerialize -- so it
     * reflects the post-edit availability. Read-only; never mutates a booking.
     */
    private List<AffectedBookingResponse> affectedBookings(UserEntity user) {
        return availability.findAffectedBookings(user);
    }

    /**
     * Resolves the caller's own {@code guide_profiles.id} for {@link #getOverridePreview} (the only
     * route here that needs a bare guide id rather than a {@link UserEntity} -- {@link
     * AvailabilityPreviewService#preview} takes the id directly). Deliberately REPLICATED (not
     * extracted into a shared helper) from {@link AvailabilityWriteService}'s private {@code
     * requireGuideId} / {@link AvailabilityReadService}'s private {@code requireGuideId} -- the
     * same 3-line lookup, kept independent per this codebase's convention (see {@link
     * AvailabilityReadService}'s class javadoc for the same rationale).
     */
    private UUID requireGuideId(UserEntity user) {
        GuideProfileEntity guide =
                guides.findByUserId(user.getId())
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "No guide profile -- complete guide onboarding"
                                                        + " first"));
        return guide.getId();
    }
}
