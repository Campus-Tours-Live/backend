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
import com.CampusToursLive.web.dto.OverrideMultiPreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.OverrideReplaceRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
import com.CampusToursLive.web.dto.RulesReplaceRequest;
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
 * Guide-facing availability WRITE API: create/update/delete recurring rules and one-off exceptions,
 * plus read/update booking settings. Every route is GENERIC — no {@code /guide} prefix — the
 * caller's role and guide id come from the security context ({@link
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
     * The guide's resolved availability: editable rules + backend-coalesced occurrences + DST
     * gap-days -- the single source of truth CTL-55/CTL-56 render read-only without re-coalescing.
     * {@code from} / {@code to} (ISO {@code yyyy-MM-dd}) optionally narrow the returned occurrences
     * to those intersecting {@code [from, to)}; omitted, every materialized occurrence is returned.
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
     * Date-specific override dry-run/preview: given a proposed override that has NOT been saved,
     * returns the resulting net-available windows per date exactly as an actual save would produce
     * them, plus which existing exception segments the override would trim -- WITHOUT persisting
     * anything. Owner-scoped like every other route here: the guide id is resolved from the
     * caller's own guide profile, never from the request.
     */
    @Operation(
            summary = "Preview a date-specific override (dry-run, no persist)",
            description =
                    "Given a proposed date-specific override that has NOT been saved, returns the"
                            + " resulting net-available windows per date exactly as an actual save"
                            + " would produce them, plus which existing exception segments the"
                            + " override would trim -- WITHOUT persisting anything. Read-only:"
                            + " nothing is written to the database. Owner-scoped like every other"
                            + " route here: the guide id is resolved from the caller's own guide"
                            + " profile, never from the request.")
    @ApiResponse(
            responseCode = "200",
            description =
                    "The dry-run preview: per-date resulting net-available windows, trimmed"
                            + " exception segments, and validity.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.OVERRIDE_PREVIEW)))
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
                    "dateFrom/dateTo/kind/startLocal/windowMin missing or invalid, including the"
                            + " override crossing midnight (startLocal + windowMin > 1440) or the"
                            + " dateFrom..dateTo range exceeding 366 days."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping("/preview")
    public ApiEnvelope<OverridePreviewResponse> getOverridePreview(
            @Parameter(
                            description = "ISO-8601 inclusive start date of the previewed range.",
                            required = true)
                    @RequestParam
                    String dateFrom,
            @Parameter(
                            description = "ISO-8601 inclusive end date of the previewed range.",
                            required = true)
                    @RequestParam
                    String dateTo,
            @Parameter(
                            description =
                                    "UNAVAILABLE removes availability; ADDITIONAL adds"
                                            + " availability.",
                            required = true)
                    @RequestParam
                    String kind,
            @Parameter(
                            description = "Wall-clock start time in the guide's settings timezone.",
                            required = true)
                    @RequestParam
                    String startLocal,
            @Parameter(description = "Window length in minutes.", required = true) @RequestParam
                    Integer windowMin) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        UUID guideId = requireGuideId(user);
        OverridePreviewRequest req =
                new OverridePreviewRequest(dateFrom, dateTo, kind, startLocal, windowMin);
        return ApiEnvelope.of(availabilityPreview.preview(guideId, req));
    }

    /**
     * Multi-window date-specific override dry-run/preview: given a proposed override made of
     * MULTIPLE time windows (multiple slots on one date or date range) that have NOT been saved,
     * returns ONE combined resulting net-available window set per date -- all windows applied
     * together (newest-wins trim/replace across the windows too) exactly as an actual sequence of
     * saves would produce -- plus which existing exception segments any window would trim, WITHOUT
     * persisting anything. This is the additive companion to the single-window {@code GET
     * /availability/preview}: the frontend sends the full {@code windows} list and the backend
     * computes the net result, so the frontend never merges N single-window previews itself. A POST
     * with a body is the clean way to carry {@code windows[]}. Owner-scoped like every other route
     * here: the guide id is resolved from the caller's own guide profile, never the request.
     */
    @Operation(
            summary = "Preview a multi-window date-specific override (dry-run, no persist)",
            description =
                    "Given a proposed date-specific override made of MULTIPLE time windows (multiple"
                            + " slots on one date or date range) that have NOT been saved, returns"
                            + " one combined resulting net-available window set per date -- all"
                            + " windows applied together (newest-wins trim/replace across the"
                            + " windows too) exactly as an actual sequence of saves would produce --"
                            + " plus which existing exception segments any window would trim,"
                            + " WITHOUT persisting anything. Read-only: nothing is written to the"
                            + " database. Additive companion to the single-window GET"
                            + " /availability/preview. Owner-scoped: the guide id is resolved from"
                            + " the caller's own guide profile, never from the request.")
    @ApiResponse(
            responseCode = "200",
            description =
                    "The combined dry-run preview: per-date resulting net-available windows (all"
                            + " windows applied together), trimmed exception segments, and validity.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.OVERRIDE_PREVIEW)))
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
                    "dateFrom/dateTo/kind invalid, windows empty when replaceExisting is not true,"
                            + " or a window's startLocal/windowMin missing or invalid, including a"
                            + " window crossing midnight (startLocal + windowMin > 1440) or the"
                            + " dateFrom..dateTo range exceeding 366 days."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/preview")
    public ApiEnvelope<OverridePreviewResponse> postOverrideMultiPreview(
            @RequestBody OverrideMultiPreviewRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        UUID guideId = requireGuideId(user);
        return ApiEnvelope.of(availabilityPreview.previewMulti(guideId, req));
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
                            + " surfaces any of the guide's own future CONFIRMED"
                            + " bookings this edit left uncovered by any occurrence; the edit still"
                            + " succeeds and no booking is mutated. Contiguous or overlapping"
                            + " ACTIVE rules on the same day of week with the same effectiveFrom/"
                            + " effectiveTo are coalesced into one stored rule on write; the"
                            + " response is that merged rule (its id may differ from a rule id you"
                            + " referenced earlier).")
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
                    "Missing/invalid fields (dayOfWeek, startLocal, windowMin), the time range"
                            + " crossing midnight (startLocal + windowMin > 1440), or effectiveTo"
                            + " before effectiveFrom. A time overlap/touch with another ACTIVE rule"
                            + " on the same day is NOT rejected when effectiveFrom/effectiveTo also"
                            + " match — that case is coalesced (merged) into one rule instead, see"
                            + " above; a DIFFERENT-but-overlapping effective range (e.g. two"
                            + " overlapping seasons) is still rejected."
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
                            + " occurrences and the response surfaces any newly"
                            + " uncovered future CONFIRMED bookings; the edit still succeeds and no"
                            + " booking is mutated. Contiguous or overlapping ACTIVE rules on the"
                            + " same day of week with the same effectiveFrom/effectiveTo are"
                            + " coalesced into one stored rule on write; the response is that"
                            + " merged rule (its id may differ from the id you updated).")
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
                    "Missing/invalid fields (dayOfWeek, startLocal, windowMin), the time range"
                            + " crossing midnight (startLocal + windowMin > 1440), or effectiveTo"
                            + " before effectiveFrom. A time overlap/touch with another ACTIVE rule"
                            + " on the same day is NOT rejected when effectiveFrom/effectiveTo also"
                            + " match — that case is coalesced (merged) into one rule instead, see"
                            + " above; a DIFFERENT-but-overlapping effective range (e.g. two"
                            + " overlapping seasons) is still rejected."
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
                            + " and the response surfaces any newly uncovered"
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
                            + " the guide's occurrences and the response surfaces"
                            + " any newly uncovered future CONFIRMED bookings; the edit still"
                            + " succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description =
                    "The created exception, plus any newly-uncovered bookings (advisory). For a"
                            + " multi-day range (dateFrom/dateTo), the response body represents"
                            + " the exception created for dateFrom -- use GET"
                            + " /availability/exceptions to retrieve the full expanded per-date"
                            + " set.",
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
                            + " the guide's occurrences and the response surfaces"
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
                            + " occurrences and the response surfaces any newly"
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

    /**
     * Atomic single-day override replace: replaces the guide's same-kind exceptions on one date
     * with exactly the supplied windows, in one transaction under the per-guide advisory lock. An
     * empty windows list clears that kind for the day; other-kind exceptions on the date are
     * preserved. Owner-scoped like every other route here: the guide id is resolved from the
     * caller's own guide profile, never from the request.
     */
    @Operation(
            summary = "Atomically replace a date's overrides for one kind",
            description =
                    "Replaces the guide's same-kind (UNAVAILABLE or ADDITIONAL) date-specific"
                            + " exceptions on the given date with exactly the supplied windows, in"
                            + " ONE transaction under a per-guide advisory lock. An empty windows"
                            + " list clears that kind for the day; other-kind exceptions on the date"
                            + " are preserved (trimmed only where a new window overlaps them,"
                            + " newest-wins). Validation runs BEFORE any mutation, and any"
                            + " mid-transaction failure rolls the whole replace back, so a prior"
                            + " override is never partially lost. The write re-materializes the"
                            + " guide's occurrences in the same transaction and the response"
                            + " surfaces any newly uncovered future CONFIRMED"
                            + " bookings; the edit still succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description =
                    "The date's resulting stored exceptions after the replace, plus any"
                            + " newly-uncovered bookings (advisory).",
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
            responseCode = "422",
            description =
                    "date/kind missing or invalid, or a window's startLocal/windowMin missing or"
                            + " invalid, including a window crossing midnight (startLocal +"
                            + " windowMin > 1440). Rejected BEFORE any mutation, so a prior override"
                            + " is left intact."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/overrides/replace")
    public AvailabilityWriteResponse<List<AvailabilityExceptionResponse>> replaceOverrides(
            @RequestBody OverrideReplaceRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        UUID guideId = requireGuideId(user);
        List<AvailabilityExceptionResponse> data = availability.replaceOverrides(guideId, req);
        return AvailabilityWriteResponse.of(data, affectedBookings(user));
    }

    /**
     * Atomic weekly-rule replace: replaces the guide's ACTIVE recurring rules for one day of week
     * with exactly the supplied windows, in one transaction under the per-guide advisory lock. An
     * empty windows list clears that weekday's rules; every other weekday is left untouched.
     * Owner-scoped like every other route here: the guide id is resolved from the caller's own
     * guide profile, never from the request.
     */
    @Operation(
            summary = "Atomically replace one weekday's availability rules",
            description =
                    "Replaces the guide's ACTIVE recurring rules for the given day of week with"
                            + " exactly the supplied windows, in ONE transaction under a per-guide"
                            + " advisory lock. An empty windows list clears that weekday's rules;"
                            + " every other weekday — and any inactive rule on this weekday — is left"
                            + " untouched. Each inserted rule takes the guide's settings timezone, an"
                            + " open-ended effective range starting today, and is active. Per-window"
                            + " validation (same-day: a window may not cross midnight) runs BEFORE any"
                            + " mutation; overlapping or touching windows are NOT rejected — they are"
                            + " accepted and coalesced (merged) into disjoint maximal rules, the same"
                            + " accept-and-resolve behaviour as single-rule create. Any"
                            + " mid-transaction failure rolls the whole replace back, so prior"
                            + " rules are never partially lost. The write re-materializes the guide's"
                            + " occurrences in the same transaction and the response surfaces (CTL-54"
                            + " Task 7) any newly uncovered future CONFIRMED bookings; the edit still"
                            + " succeeds and no booking is mutated.")
    @ApiResponse(
            responseCode = "200",
            description =
                    "The weekday's resulting active rules after the replace, plus any"
                            + " newly-uncovered bookings (advisory).",
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
            responseCode = "422",
            description =
                    "dayOfWeek missing or out of range (0-6), or a window's startLocal/windowMin"
                            + " missing or invalid, including a window crossing midnight (startLocal"
                            + " + windowMin > 1440). Overlapping or touching windows are NOT a 422 —"
                            + " they are coalesced (merged) into disjoint rules and the replace"
                            + " succeeds (200). A validation failure is raised BEFORE any mutation, so"
                            + " prior rules are left intact."
                            + NO_GUIDE_PROFILE_422,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/rules/replace")
    public AvailabilityWriteResponse<List<AvailabilityRuleResponse>> replaceRules(
            @RequestBody RulesReplaceRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        UUID guideId = requireGuideId(user);
        List<AvailabilityRuleResponse> data = availability.replaceRules(guideId, req);
        return AvailabilityWriteResponse.of(data, affectedBookings(user));
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
     * Resolves the caller's own {@code guide_profiles.id} for the routes that need a bare guide id
     * rather than a {@link UserEntity} -- the two preview endpoints and the two replace endpoints.
     * ({@link AvailabilityPreviewService#preview} takes the id directly). Deliberately REPLICATED
     * (not extracted into a shared helper) from {@link AvailabilityWriteService}'s private {@code
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
