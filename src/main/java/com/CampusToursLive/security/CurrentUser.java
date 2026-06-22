package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal (a Google id_token) to a domain user, looked up by
 * oidc_subject (the OIDC "sub").
 *
 * <p>Accounts are NOT created implicitly on every request — provisioning only happens through
 * {@link #resolve(String)} with a "signup" intent, so that signing in with an unregistered Google
 * account is rejected rather than silently creating an account.
 */
@Component
public class CurrentUser {

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final UserProvisioningService provisioning;

    public CurrentUser(
            UserRepository users,
            UserRoleRepository userRoles,
            UserProvisioningService provisioning) {
        this.users = users;
        this.userRoles = userRoles;
        this.provisioning = provisioning;
    }

    /** The current authenticated user. Must already be provisioned (via {@link #resolve}). */
    public UserEntity require() {
        Jwt jwt = currentJwt();
        return users.findByOidcSubject(jwt.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Account not provisioned"));
    }

    /**
     * Authorize a role-scoped action against the authoritative role set in {@code user_roles}. Used
     * by future supply-/demand-side endpoints (e.g. guide listings → GUIDE); onboarding endpoints
     * that GRANT a role must NOT call this (they'd 403 the very first acquisition). Authorization
     * always reads {@code user_roles}, never the active role.
     */
    public UserEntity requireRole(UserRole role) {
        UserEntity user = require();
        if (!userRoles.existsByUserIdAndRole(user.getId(), role)) {
            throw new ForbiddenException("Missing required role: " + role);
        }
        return user;
    }

    /**
     * Resolve a login by intent:
     *
     * <ul>
     *   <li>existing account → returned as-is (either intent);
     *   <li>new subject + "signup" → provisioned;
     *   <li>new subject + "signin" → 404, so the web app can send the user to sign up.
     * </ul>
     */
    public UserEntity resolve(String intent) {
        Jwt jwt = currentJwt();
        Optional<UserEntity> existing = users.findByOidcSubject(jwt.getSubject());
        if (existing.isPresent()) {
            return existing.get();
        }
        if ("signup".equalsIgnoreCase(intent)) {
            return provisionOrGet(jwt);
        }
        throw new NotFoundException(
                "No account is registered for this Google account. Please sign up first.");
    }

    private Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new UnauthorizedException("Authentication required");
    }

    /**
     * Provision a brand-new subject, tolerating the race where several concurrent requests for a
     * first-time user each try to INSERT: only one wins, the rest hit the unique constraint on
     * oidc_subject — for those, return the row the winner just created instead of failing.
     */
    private UserEntity provisionOrGet(Jwt jwt) {
        try {
            return provisioning.provisionFromJwt(jwt);
        } catch (DataIntegrityViolationException raceLost) {
            return users.findByOidcSubject(jwt.getSubject()).orElseThrow(() -> raceLost);
        }
    }
}
