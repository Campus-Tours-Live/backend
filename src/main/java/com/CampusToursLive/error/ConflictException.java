package com.CampusToursLive.error;

import java.util.Map;

/**
 * A request conflicts with the current state of a resource — a role already granted, an ineligible
 * role grant, a data-integrity invariant broken, or a reschedule rule violation. Framework-agnostic
 * — the web layer maps it to HTTP 409 (see {@code GlobalExceptionHandler}); the domain stays free
 * of Spring Web types.
 *
 * <p>Always carries a machine-readable {@link CodedProblem#code()}; construct instances only via
 * the factories below so Core/bff/OpenAPI keep sharing the exact same codes.
 */
public final class ConflictException extends RuntimeException implements CodedProblem {

    private final String code;
    private final transient Map<String, Object> properties;

    private ConflictException(String message, String code, Map<String, Object> properties) {
        super(message);
        this.code = code;
        this.properties = properties;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public Map<String, Object> properties() {
        return properties;
    }

    /**
     * The caller already holds {@code role}. {@code reconciliationRequired:true} signals the client
     * to reconcile via {@code /userinfo} (lost-response retry) rather than treat this as a hard
     * failure.
     */
    public static ConflictException roleAlreadyGranted(String role) {
        return new ConflictException(
                "Role already granted: " + role,
                "ROLE_ALREADY_GRANTED",
                Map.of("role", role, "reconciliationRequired", true));
    }

    /**
     * The account is not eligible to acquire {@code role} (e.g. a PARENT participant onboarding as
     * guide).
     */
    public static ConflictException roleNotEligible(String role) {
        return new ConflictException(
                "Not eligible for role: " + role, "ROLE_NOT_ELIGIBLE", Map.of("role", role));
    }

    /**
     * Data-integrity violation: a committed account has no roles. Never a normal or resumable
     * state.
     */
    public static ConflictException accountStateInvalid() {
        return new ConflictException(
                "Account is in an invalid state", "ACCOUNT_STATE_INVALID", Map.of());
    }

    /**
     * Data-integrity violation: the account holds {@code role} but its role-profile is missing (a
     * broken role &harr; profile pairing).
     */
    public static ConflictException roleProfileStateInvalid(String role) {
        return new ConflictException(
                "Role profile missing for role: " + role,
                "ROLE_PROFILE_STATE_INVALID",
                Map.of("role", role));
    }

    /** CTL-50: only CONFIRMED bookings accept a reschedule proposal. */
    public static ConflictException bookingNotConfirmedForReschedule() {
        return new ConflictException(
                "Only a confirmed booking can be rescheduled",
                "BOOKING_NOT_CONFIRMED_FOR_RESCHEDULE",
                Map.of());
    }

    /** CTL-50: a booking that has already started cannot be moved. */
    public static ConflictException bookingAlreadyStarted() {
        return new ConflictException(
                "A booking that has already started cannot be rescheduled",
                "BOOKING_ALREADY_STARTED",
                Map.of());
    }

    /**
     * CTL-50: at most one PENDING_COUNTERPARTY proposal per booking ({@code uq_reschedule_active}).
     */
    public static ConflictException rescheduleAlreadyPending() {
        return new ConflictException(
                "A reschedule proposal is already pending for this booking",
                "RESCHEDULE_ALREADY_PENDING",
                Map.of());
    }

    /** CTL-50 / CTL-54: proposed interval is not covered by guide availability. */
    public static ConflictException proposedOutsideAvailability() {
        return new ConflictException(
                "The proposed time is outside the guide's availability",
                "PROPOSED_OUTSIDE_AVAILABILITY",
                Map.of());
    }

    /** CTL-50: proposed slot overlaps another holding booking for the same guide. */
    public static ConflictException guideSlotConflict() {
        return new ConflictException(
                "The guide already has a booking at the proposed time",
                "GUIDE_SLOT_CONFLICT",
                Map.of());
    }

    /** CTL-50: proposed slot overlaps another holding booking for the same participant. */
    public static ConflictException participantSlotConflict() {
        return new ConflictException(
                "The participant already has a booking that overlaps the proposed time",
                "PARTICIPANT_SLOT_CONFLICT",
                Map.of());
    }
}
