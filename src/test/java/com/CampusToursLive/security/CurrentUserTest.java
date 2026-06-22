package com.CampusToursLive.security;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * CurrentUser.requireRole — the role gate behind every role-scoped endpoint. Authorization reads
 * user_roles, never the active role.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

    @Mock UserRepository users;
    @Mock UserRoleRepository userRoles;
    @Mock UserProvisioningService provisioning;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Put an authenticated Jwt principal (subject) into the security context. */
    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        // 2-arg ctor marks the token authenticated (the 1-arg one does not).
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    private CurrentUser currentUser() {
        return new CurrentUser(users, userRoles, provisioning);
    }

    @Test
    void requireRole_returnsUser_whenRoleHeld() {
        UUID uid = UUID.randomUUID();
        UserEntity u = new UserEntity();
        u.setId(uid);
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.of(u));
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(true);
        authenticate("sub-1");

        assertSame(u, currentUser().requireRole(UserRole.GUIDE));
    }

    @Test
    void requireRole_throws403_whenRoleNotHeld() {
        UUID uid = UUID.randomUUID();
        UserEntity u = new UserEntity();
        u.setId(uid);
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.of(u));
        when(userRoles.existsByUserIdAndRole(uid, UserRole.GUIDE)).thenReturn(false);
        authenticate("sub-1");

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class, () -> currentUser().requireRole(UserRole.GUIDE));
        assertInstanceOf(ForbiddenException.class, ex);
    }

    // ---- require() ------------------------------------------------------------------------

    @Test
    void require_throws401_whenNoAuthentication() {
        // No principal in the context (cleared by @AfterEach of the previous test / fresh thread).
        RuntimeException ex = assertThrows(RuntimeException.class, () -> currentUser().require());
        assertInstanceOf(UnauthorizedException.class, ex);
    }

    @Test
    void require_throws401_whenSubjectNotProvisioned() {
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.empty());
        authenticate("sub-1");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> currentUser().require());
        assertInstanceOf(UnauthorizedException.class, ex);
    }

    // ---- resolve(intent) ------------------------------------------------------------------

    @Test
    void resolve_returnsExisting_regardlessOfIntent() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.of(u));
        authenticate("sub-1");

        assertSame(u, currentUser().resolve("signin"));
    }

    @Test
    void resolve_provisions_whenSignupAndNewSubject() {
        UserEntity provisioned = new UserEntity();
        provisioned.setId(UUID.randomUUID());
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.empty());
        when(provisioning.provisionFromJwt(any())).thenReturn(provisioned);
        authenticate("sub-1");

        assertSame(provisioned, currentUser().resolve("signup"));
    }

    @Test
    void resolve_throws404_whenSigninAndNewSubject() {
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.empty());
        authenticate("sub-1");

        RuntimeException ex =
                assertThrows(RuntimeException.class, () -> currentUser().resolve("signin"));
        assertInstanceOf(NotFoundException.class, ex);
    }

    @Test
    void resolve_recoversFromProvisioningRace() {
        UserEntity winnerRow = new UserEntity();
        winnerRow.setId(UUID.randomUUID());
        // empty on the first (resolve) lookup, then present on the post-race recovery lookup.
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.empty(), Optional.of(winnerRow));
        when(provisioning.provisionFromJwt(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate oidc_subject"));
        authenticate("sub-1");

        assertSame(winnerRow, currentUser().resolve("signup"));
    }

    // ---- currentJwt() rejection branches -------------------------------------------------

    @Test
    void require_throws401_whenAuthenticationIsNotAuthenticated() {
        // Present but not authenticated (2-arg token has isAuthenticated() == false).
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("u", "p"));
        assertThrows(UnauthorizedException.class, () -> currentUser().require());
    }

    @Test
    void require_throws401_whenPrincipalIsNotAJwt() {
        // Authenticated, but the principal is not a Jwt → still rejected.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("u", "p", Collections.emptyList()));
        assertThrows(UnauthorizedException.class, () -> currentUser().require());
    }

    @Test
    void resolve_rethrowsRaceError_whenRecoveryFindsNothing() {
        // provisioning loses the race AND the recovery lookup also misses → the original
        // DataIntegrityViolationException is rethrown (covers the orElseThrow lambda).
        when(users.findByOidcSubject("sub-1")).thenReturn(Optional.empty(), Optional.empty());
        when(provisioning.provisionFromJwt(any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        authenticate("sub-1");

        assertThrows(DataIntegrityViolationException.class, () -> currentUser().resolve("signup"));
    }
}
