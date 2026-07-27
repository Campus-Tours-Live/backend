package com.CampusToursLive.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central, idempotent role acquisition. Called when a role's onboarding completes (guide submit /
 * participant save). Writing the {@code user_roles} row IS the "role granted" signal.
 *
 * <p>Active-role/session context is owned by the BFF session, not Core — this service only inserts
 * the {@code user_roles} row and never touches {@code user}. Kept as a no-arg-mutation service (not
 * a static helper) so it stays the single place role acquisition is defined.
 */
@Service
public class RoleGrantService {

    private final UserRoleRepository userRoles;

    public RoleGrantService(UserRoleRepository userRoles) {
        this.userRoles = userRoles;
    }

    @Transactional
    public void grant(UserEntity user, UserRole role) {
        if (userRoles.existsByUserIdAndRole(user.getId(), role)) {
            return; // already held — no-op
        }
        userRoles.save(new UserRoleEntity(user.getId(), role)); // granted_at defaults to now()
    }
}
