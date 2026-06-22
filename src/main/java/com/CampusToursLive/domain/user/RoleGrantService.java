package com.CampusToursLive.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central, idempotent role acquisition. Called when a role's onboarding completes (guide submit /
 * participant save). Writing the {@code user_roles} row IS the "role granted" signal; the role is
 * only made the active role on FIRST acquisition, so editing an existing profile never yanks the
 * user's current area.
 *
 * <p>NOTE: this only inserts the {@code user_roles} row and updates {@code user.lastActiveRole} in
 * memory. The CALLER persists {@code user} ({@code users.save}) within the same transaction —
 * callers already save the user for their own field updates, so grant() deliberately does NOT, to
 * avoid a redundant double-save.
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
            return; // already held — no-op (don't change active role on re-edits)
        }
        userRoles.save(new UserRoleEntity(user.getId(), role)); // granted_at defaults to now()
        // Only a switchable role becomes the active context. Staff roles (ADMIN/SUPPORT)
        // are granted (the user_roles row above) but never auto-activated — otherwise a
        // staff-only account would end up with active=ADMIN, which has no /dashboard view.
        // The same SWITCHABLE set guards the switch-active-role endpoint.
        if (UserRole.SWITCHABLE.contains(role)) {
            user.setLastActiveRole(role);
        }
        // Caller persists `user` (see GuideService / ParticipantService).
    }
}
