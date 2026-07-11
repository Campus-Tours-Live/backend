package com.CampusToursLive.web;

import com.CampusToursLive.domain.availability.AvailabilityReadService;
import com.CampusToursLive.domain.availability.AvailabilityWriteService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.AffectedBookingResponse;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilityWriteResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsUpdateRequest;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
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

    private final CurrentUser currentUser;
    private final AvailabilityWriteService availability;
    private final AvailabilityReadService availabilityRead;

    public AvailabilityController(
            CurrentUser currentUser,
            AvailabilityWriteService availability,
            AvailabilityReadService availabilityRead) {
        this.currentUser = currentUser;
        this.availability = availability;
        this.availabilityRead = availabilityRead;
    }

    /**
     * The guide's resolved availability (CTL-54 Task 5b): editable rules + backend-coalesced
     * occurrences + DST gap-days -- the single source of truth CTL-55/CTL-56 render read-only
     * without re-coalescing. {@code from} / {@code to} (ISO {@code yyyy-MM-dd}) optionally narrow
     * the returned occurrences to those intersecting {@code [from, to)}; omitted, every
     * materialized occurrence is returned.
     */
    @GetMapping
    public ApiEnvelope<ResolvedAvailabilityResponse> getResolvedAvailability(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availabilityRead.getResolvedAvailability(user, from, to));
    }

    /** List the guide's recurring availability rules. */
    @GetMapping("/rules")
    public ApiEnvelope<List<AvailabilityRuleResponse>> listRules() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.listRules(user));
    }

    /** Create a recurring availability rule; timezone is server-set to the guide's settings tz. */
    @PostMapping("/rules")
    public AvailabilityWriteResponse<AvailabilityRuleResponse> createRule(
            @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityRuleResponse created = availability.createRule(user, req);
        return AvailabilityWriteResponse.of(created, affectedBookings(user));
    }

    /** Update an owned rule (404 if the id does not belong to the caller). */
    @PatchMapping("/rules/{id}")
    public AvailabilityWriteResponse<AvailabilityRuleResponse> updateRule(
            @PathVariable UUID id, @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityRuleResponse updated = availability.updateRule(user, id, req);
        return AvailabilityWriteResponse.of(updated, affectedBookings(user));
    }

    /** Delete an owned rule (404 if not owned); returns the guide's remaining rules. */
    @DeleteMapping("/rules/{id}")
    public AvailabilityWriteResponse<List<AvailabilityRuleResponse>> deleteRule(
            @PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        List<AvailabilityRuleResponse> remaining = availability.deleteRule(user, id);
        return AvailabilityWriteResponse.of(remaining, affectedBookings(user));
    }

    /** List the guide's one-off availability exceptions. */
    @GetMapping("/exceptions")
    public ApiEnvelope<List<AvailabilityExceptionResponse>> listExceptions() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.listExceptions(user));
    }

    /** Create a one-off availability exception (UNAVAILABLE or ADDITIONAL). */
    @PostMapping("/exceptions")
    public AvailabilityWriteResponse<AvailabilityExceptionResponse> createException(
            @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityExceptionResponse created = availability.createException(user, req);
        return AvailabilityWriteResponse.of(created, affectedBookings(user));
    }

    /** Update an owned exception (404 if the id does not belong to the caller). */
    @PatchMapping("/exceptions/{id}")
    public AvailabilityWriteResponse<AvailabilityExceptionResponse> updateException(
            @PathVariable UUID id, @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        AvailabilityExceptionResponse updated = availability.updateException(user, id, req);
        return AvailabilityWriteResponse.of(updated, affectedBookings(user));
    }

    /** Delete an owned exception (404 if not owned); returns the guide's remaining exceptions. */
    @DeleteMapping("/exceptions/{id}")
    public AvailabilityWriteResponse<List<AvailabilityExceptionResponse>> deleteException(
            @PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        List<AvailabilityExceptionResponse> remaining = availability.deleteException(user, id);
        return AvailabilityWriteResponse.of(remaining, affectedBookings(user));
    }

    /** The guide's booking settings; auto-provisions a default row the first time it is asked. */
    @GetMapping("/settings")
    public ApiEnvelope<GuideBookingSettingsResponse> getSettings() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.getSettings(user));
    }

    /**
     * Update the guide's booking settings. Changing {@code timezone} cascades the new zone onto
     * every existing rule (the read-only-tz invariant) before re-materializing.
     */
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
}
