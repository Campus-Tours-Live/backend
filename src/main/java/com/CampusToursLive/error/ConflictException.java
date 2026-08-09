package com.CampusToursLive.error;

import java.util.Map;

/**
 * A request conflicts with the current state of a resource — a role already granted, an ineligible
 * role grant, or a data-integrity invariant broken. Framework-agnostic — the web layer maps it to
 * HTTP 409 (see {@code GlobalExceptionHandler}); the domain stays free of Spring Web types.
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
}
