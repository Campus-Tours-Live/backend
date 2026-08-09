package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * CurrentUser.requireRole(UserRole) — the legacy role gate behind the ~28 supply/demand-side
 * endpoints (Availability/GuideOffering/Cart/OfferingSlot/Booking) that still consume a MANAGED
 * {@link UserEntity} rather than a typed {@link RoleAccountContext}. Reimplemented on top of {@link
 * CurrentUser#requireProvisioned()} (CTL-97 Task 6): a pending caller now gets 404 {@code
 * ACCOUNT_NOT_PROVISIONED} instead of a bare 401 (I10), and the resolver's whole-account validation
 * applies before the role check even runs.
 *
 * <p>Also covers the private {@code currentJwt()} rejection branches (exercised through {@link
 * CurrentUser#requireRole(UserRole)}, since the OAuth-time JIT-provisioning path was removed in
 * CTL-97 — provisioning is onboarding-only now, see {@code UserProvisioningServiceTest} / {@code
 * OnboardingServiceTest}). {@code require()} was removed in Task 6 — {@code SessionController.me()}
 * was its only caller and now uses {@code requireProvisioned()} directly ({@link
 * CurrentUserAuthzTest} covers that method's full outcome matrix).
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SUBJECT = "sub-1";

    @Mock UserRepository users;
    @Mock AccountResolver accountResolver;
    @Mock GuideProfileRepository guideProfiles;
    @Mock ParticipantProfileRepository participantProfiles;

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
        return new CurrentUser(users, accountResolver, guideProfiles, participantProfiles);
    }

    private static ProvisionedAccount provisionedAccount(UserRole... roles) {
        return new ProvisionedAccount(
                USER_ID,
                SUBJECT,
                "ada@example.com",
                "Ada",
                "Lovelace",
                "Ada Lovelace",
                AccountStatus.ACTIVE,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Set.of(roles));
    }

    // ---- requireRole(UserRole) --------------------------------------------------------------

    @Test
    void requireRole_returnsManagedUser_whenRoleHeld() {
        when(accountResolver.resolveAuthenticatedIdentity(any()))
                .thenReturn(new AccountResolution.Provisioned(provisionedAccount(UserRole.GUIDE)));
        UserEntity managed = new UserEntity();
        managed.setId(USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(managed));
        authenticate(SUBJECT);

        assertSame(managed, currentUser().requireRole(UserRole.GUIDE));
    }

    @Test
    void requireRole_throws404_withCode_whenPending() {
        when(accountResolver.resolveAuthenticatedIdentity(any()))
                .thenReturn(new AccountResolution.Pending());
        authenticate(SUBJECT);

        assertThatThrownBy(() -> currentUser().requireRole(UserRole.GUIDE))
                .isInstanceOf(NotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((NotFoundException) ex).code())
                                        .isEqualTo("ACCOUNT_NOT_PROVISIONED"));
    }

    @Test
    void requireRole_throws403_withCode_whenRoleNotHeld() {
        when(accountResolver.resolveAuthenticatedIdentity(any()))
                .thenReturn(
                        new AccountResolution.Provisioned(
                                provisionedAccount(UserRole.PARTICIPANT)));
        authenticate(SUBJECT);

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class, () -> currentUser().requireRole(UserRole.GUIDE));
        assertInstanceOf(ForbiddenException.class, ex);
        assertThat(((ForbiddenException) ex).code()).isEqualTo("ROLE_REQUIRED");
    }

    @Test
    void requireRole_throws409_accountStateInvalid_whenManagedRowMissingDespiteResolution() {
        // Defensive branch: the resolver just vouched for the row in this same request, so a miss
        // on the immediate findById is a broken invariant, not an ordinary "not found".
        when(accountResolver.resolveAuthenticatedIdentity(any()))
                .thenReturn(new AccountResolution.Provisioned(provisionedAccount(UserRole.GUIDE)));
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        authenticate(SUBJECT);

        assertThatThrownBy(() -> currentUser().requireRole(UserRole.GUIDE))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ACCOUNT_STATE_INVALID"));
    }

    // ---- currentJwt() rejection branches (exercised via requireRole/requireProvisioned) ------

    @Test
    void requireRole_throws401_whenNoAuthentication() {
        // No principal in the context (cleared by @AfterEach of the previous test / fresh thread).
        RuntimeException ex =
                assertThrows(
                        RuntimeException.class, () -> currentUser().requireRole(UserRole.GUIDE));
        assertInstanceOf(UnauthorizedException.class, ex);
    }

    @Test
    void requireRole_throws401_whenAuthenticationIsNotAuthenticated() {
        // Present but not authenticated (2-arg token has isAuthenticated() == false).
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("u", "p"));
        assertThrows(UnauthorizedException.class, () -> currentUser().requireRole(UserRole.GUIDE));
    }

    @Test
    void requireRole_throws401_whenPrincipalIsNotAJwt() {
        // Authenticated, but the principal is not a Jwt → still rejected.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("u", "p", Collections.emptyList()));
        assertThrows(UnauthorizedException.class, () -> currentUser().requireRole(UserRole.GUIDE));
    }
}
