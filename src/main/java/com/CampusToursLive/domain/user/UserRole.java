package com.CampusToursLive.domain.user;

import java.util.EnumSet;
import java.util.Set;

/** Matches the PostgreSQL enum type {@code user_role}. */
public enum UserRole {
    PARTICIPANT,
    GUIDE,
    ADMIN,
    SUPPORT;

    /**
     * Roles a user can self-acquire and switch their active context to. Staff roles (ADMIN/SUPPORT)
     * are externally granted and never become the active role — the shared /dashboard has no view
     * for them.
     */
    public static final Set<UserRole> SWITCHABLE = EnumSet.of(PARTICIPANT, GUIDE);
}
