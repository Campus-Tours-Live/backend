package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable, read-only snapshot of a healthy account, built directly from the {@link
 * com.CampusToursLive.domain.user.AccountProjection} row — never from a managed or detached {@code
 * UserEntity}. Callers get a plain value, not something that can be saved back and accidentally
 * mutate the database.
 */
public record ProvisionedAccount(
        UUID userId,
        String oidcSubject,
        String email,
        String firstName,
        String lastName,
        String displayName,
        AccountStatus accountStatus,
        AgeBand ageBand,
        Instant createdAt,
        Set<UserRole> roles) {}
