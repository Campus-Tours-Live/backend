package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.UserRole;

/**
 * A resolved, role-scoped view of the current caller: always carries the underlying {@link
 * ProvisionedAccount}, plus — for the profile-backed roles — the profile row loaded once alongside
 * it. Built exclusively by {@link CurrentUser#requireGuide()}, {@link
 * CurrentUser#requireParticipant()}, and {@link CurrentUser#requireNonProfileRole(UserRole)}; never
 * constructed directly by callers, so a controller can't fabricate a role grant it didn't get from
 * the authoritative resolver.
 */
public sealed interface RoleAccountContext {

    ProvisionedAccount account();

    /** The caller holds GUIDE, with its (defensively re-read) {@link GuideProfileSnapshot}. */
    record Guide(ProvisionedAccount account, GuideProfileSnapshot profile)
            implements RoleAccountContext {}

    /**
     * The caller holds PARTICIPANT, with its (defensively re-read) {@link
     * ParticipantProfileSnapshot}.
     */
    record Participant(ProvisionedAccount account, ParticipantProfileSnapshot profile)
            implements RoleAccountContext {}

    /** The caller holds a profile-less role (ADMIN or SUPPORT) — never GUIDE/PARTICIPANT. */
    record NonProfile(ProvisionedAccount account, UserRole role) implements RoleAccountContext {}
}
