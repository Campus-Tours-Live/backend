package com.CampusToursLive.web;

import com.CampusToursLive.domain.availability.AvailabilityReadService;
import com.CampusToursLive.domain.availability.AvailabilityWriteService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
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
    public ApiEnvelope<AvailabilityRuleResponse> createRule(
            @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.createRule(user, req));
    }

    /** Update an owned rule (404 if the id does not belong to the caller). */
    @PatchMapping("/rules/{id}")
    public ApiEnvelope<AvailabilityRuleResponse> updateRule(
            @PathVariable UUID id, @RequestBody AvailabilityRuleRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.updateRule(user, id, req));
    }

    /** Delete an owned rule (404 if not owned); returns the guide's remaining rules. */
    @DeleteMapping("/rules/{id}")
    public ApiEnvelope<List<AvailabilityRuleResponse>> deleteRule(@PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.deleteRule(user, id));
    }

    /** List the guide's one-off availability exceptions. */
    @GetMapping("/exceptions")
    public ApiEnvelope<List<AvailabilityExceptionResponse>> listExceptions() {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.listExceptions(user));
    }

    /** Create a one-off availability exception (UNAVAILABLE or ADDITIONAL). */
    @PostMapping("/exceptions")
    public ApiEnvelope<AvailabilityExceptionResponse> createException(
            @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.createException(user, req));
    }

    /** Update an owned exception (404 if the id does not belong to the caller). */
    @PatchMapping("/exceptions/{id}")
    public ApiEnvelope<AvailabilityExceptionResponse> updateException(
            @PathVariable UUID id, @RequestBody AvailabilityExceptionRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.updateException(user, id, req));
    }

    /** Delete an owned exception (404 if not owned); returns the guide's remaining exceptions. */
    @DeleteMapping("/exceptions/{id}")
    public ApiEnvelope<List<AvailabilityExceptionResponse>> deleteException(@PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.deleteException(user, id));
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
    public ApiEnvelope<GuideBookingSettingsResponse> updateSettings(
            @RequestBody GuideBookingSettingsUpdateRequest req) {
        var user = currentUser.requireRole(UserRole.GUIDE);
        return ApiEnvelope.of(availability.updateSettings(user, req));
    }
}
