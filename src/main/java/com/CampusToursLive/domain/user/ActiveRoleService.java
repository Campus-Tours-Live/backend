package com.CampusToursLive.domain.user;

import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Switches the caller's active role — a pure UX-context change (last_active_role); authorization
 * always reads user_roles, never this. Only a held, SWITCHABLE role is allowed. The rule (and its
 * 422/403 outcomes) lives here, not in the controller, so the web layer stays a thin adapter.
 */
@Service
public class ActiveRoleService {

    private final UserRepository users;
    private final UserRoleRepository userRoles;

    public ActiveRoleService(UserRepository users, UserRoleRepository userRoles) {
        this.users = users;
        this.userRoles = userRoles;
    }

    @Transactional
    public void switchActiveRole(UserEntity user, String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new ValidationException("role is required");
        }
        UserRole target;
        try {
            target = UserRole.valueOf(rawRole.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown role: " + rawRole);
        }
        if (!UserRole.SWITCHABLE.contains(target)
                || !userRoles.existsByUserIdAndRole(user.getId(), target)) {
            throw new ForbiddenException("Role not available to switch to");
        }
        user.setLastActiveRole(target);
        users.save(user);
    }
}
