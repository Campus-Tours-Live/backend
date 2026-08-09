package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.UserRole;

/**
 * The outcome of classifying an authenticated identity (a validated {@code Jwt}) against the {@code
 * users} table in a SINGLE database snapshot — see {@link AccountResolver}.
 *
 * <p>This is a read-only classification: producing any variant other than {@link Provisioned} never
 * creates or mutates a row. Deciding what to DO about a non-provisioned result (provision, 401,
 * 403, 409-with-coded-problem, …) is the caller's responsibility (Task 3+), not this resolver's.
 */
public sealed interface AccountResolution {

    /**
     * No {@code users} row for this subject yet — never authenticated before (or never signed up).
     */
    record Pending() implements AccountResolution {}

    /**
     * A healthy, fully-provisioned account, snapshot as an immutable {@link ProvisionedAccount}.
     */
    record Provisioned(ProvisionedAccount account) implements AccountResolution {}

    /**
     * {@code account_status = SUSPENDED} — account-level moderation, not a data-integrity issue.
     */
    record Suspended() implements AccountResolution {}

    /**
     * {@code deleted_at IS NOT NULL} or {@code account_status = DELETED} — deny-safe: either signal
     * alone is enough to deny, regardless of whether the other agrees.
     */
    record Deleted() implements AccountResolution {}

    /**
     * A data-integrity problem detected in the same snapshot — never a normal or resumable state.
     */
    sealed interface Invalid extends AccountResolution {
        String code();
    }

    /** The account is active but holds no role at all. */
    record AccountStateInvalid() implements Invalid {
        public String code() {
            return "ACCOUNT_STATE_INVALID";
        }
    }

    /**
     * A profile-backed role ({@code GUIDE}/{@code PARTICIPANT}) is either held without exactly one
     * matching profile row, or an orphan profile row exists for a role the account does not hold.
     */
    record RoleProfileStateInvalid(UserRole role) implements Invalid {
        public String code() {
            return "ROLE_PROFILE_STATE_INVALID";
        }
    }
}
