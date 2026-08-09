package com.CampusToursLive.domain.user;

/**
 * Matches the PostgreSQL enum type {@code account_status} — the account-level lifecycle only
 * (ACTIVE, SUSPENDED, DELETED). Accounts are created ACTIVE: sign-in is Google OIDC, which has
 * already verified the email, so there is no separate pending-verification state. SUSPENDED /
 * DELETED are reserved for future account moderation. Role-specific lifecycle is tracked separately
 * on each profile (e.g. {@code guide_profiles.guide_status}), never on the account status.
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
