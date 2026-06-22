package com.CampusToursLive.domain.user;

/**
 * Matches the PostgreSQL enum type {@code account_status} — the account-level lifecycle only
 * (PENDING_VERIFICATION, ACTIVE, SUSPENDED, DELETED). Role-specific lifecycle is tracked separately
 * on each profile (e.g. {@code guide_profiles.application_status}), never on the account status.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DELETED
}
