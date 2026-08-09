package com.CampusToursLive.domain.user;

/**
 * Why {@code GET /users/me/role-eligibility} returned {@code eligible=false}. A typed enum (not a
 * free string) because the BFF routes on it — a silent rename would break routing.
 */
public enum RoleIneligibilityReason {
    /** A PARENT-type participant may not also become a GUIDE. */
    PARENT_CANNOT_BECOME_GUIDE,
    /** Defensive: the caller already holds the role being checked. */
    ROLE_ALREADY_HELD
}
