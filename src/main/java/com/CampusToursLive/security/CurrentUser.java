package com.CampusToursLive.security;

import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import java.util.Map;
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
    private final AccountResolver accountResolver;
    private final GuideProfileRepository guideProfiles;
    private final ParticipantProfileRepository participantProfiles;

    public CurrentUser(
            UserRepository users,
            UserRoleRepository userRoles,
            UserProvisioningService provisioning,
            AccountResolver accountResolver,
            GuideProfileRepository guideProfiles,
            ParticipantProfileRepository participantProfiles) {
        this.users = users;
        this.userRoles = userRoles;
        this.provisioning = provisioning;
        this.accountResolver = accountResolver;
        this.guideProfiles = guideProfiles;
        this.participantProfiles = participantProfiles;
    }

    /**
     * The current authenticated user. Must already be provisioned (via {@link #resolve}).
     *
     * @deprecated use {@link #requireProvisioned()} instead — it classifies through the
     *     single-snapshot {@link AccountResolver} and maps Suspended/Deleted/Invalid to their own
     *     coded problems instead of collapsing everything non-provisioned into a bare 401. Kept
     *     only until Task 6 migrates its callers.
     */
    @Deprecated(forRemoval = true)
    public UserEntity require() {
        Jwt jwt = currentJwt();
        return users.findByOidcSubject(jwt.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Account not provisioned"));
    }

    /**
     * The authoritative, single-snapshot account gate: classifies the caller via {@link
     * AccountResolver#resolveAuthenticatedIdentity} and maps every non-{@code Provisioned} outcome
     * to its coded HTTP problem —
     *
     * <ul>
     *   <li>{@code Pending} → 404 {@code ACCOUNT_NOT_PROVISIONED}
     *   <li>{@code Suspended} → 403 {@code ACCOUNT_SUSPENDED}
     *   <li>{@code Deleted} → 403 {@code ACCOUNT_DELETED}
     *   <li>{@code Invalid} (a data-integrity problem) → 409, via {@link ConflictException}
     * </ul>
     *
     * A {@code Provisioned} outcome returns its immutable {@link ProvisionedAccount} snapshot.
     */
    public ProvisionedAccount requireProvisioned() {
        Jwt jwt = currentJwt();
        AccountResolution resolution = accountResolver.resolveAuthenticatedIdentity(jwt);
        return switch (resolution) {
            case AccountResolution.Provisioned provisioned -> provisioned.account();
            case AccountResolution.Pending pending ->
                    throw new NotFoundException(
                            "Account not provisioned", "ACCOUNT_NOT_PROVISIONED");
            case AccountResolution.Suspended suspended ->
                    throw new ForbiddenException("Account is suspended", "ACCOUNT_SUSPENDED");
            case AccountResolution.Deleted deleted ->
                    throw new ForbiddenException("Account is deleted", "ACCOUNT_DELETED");
            case AccountResolution.AccountStateInvalid accountStateInvalid ->
                    throw ConflictException.accountStateInvalid();
            case AccountResolution.RoleProfileStateInvalid roleProfileStateInvalid ->
                    throw ConflictException.roleProfileStateInvalid(
                            roleProfileStateInvalid.role().name());
        };
    }

    /**
     * {@code requireProvisioned()} + the GUIDE role held + its profile loaded once.
     *
     * <p><b>resolve once &ne; query DB once</b>: account state (including the exactly-one-profile
     * pairing invariant) was already asserted inside the single {@link AccountResolver} snapshot
     * behind {@link #requireProvisioned()}. The read below is a SEPARATE, defensive re-read of
     * {@code guide_profiles} — not part of that snapshot, and not guaranteed atomic with it. Until
     * Core-B locks role-profile mutation/deletion against the grant, a profile can in principle
     * disappear between the two reads; a miss here despite a {@code Provisioned} resolution is
     * still {@code ROLE_PROFILE_STATE_INVALID} (409), never {@code PROFILE_NOT_FOUND} — the
     * resolver already vouched for the pairing, so a miss now is a broken invariant, not an
     * ordinary "not found".
     *
     * @throws ForbiddenException {@code ROLE_REQUIRED} (403) if the account does not hold GUIDE.
     * @throws ConflictException {@code ROLE_PROFILE_STATE_INVALID} (409) if GUIDE is held but the
     *     guide profile is missing.
     */
    public RoleAccountContext.Guide requireGuide() {
        ProvisionedAccount account = requireProvisioned();
        requireRoleHeld(account, UserRole.GUIDE);
        var profile =
                guideProfiles
                        .findByUserId(account.userId())
                        .orElseThrow(
                                () ->
                                        ConflictException.roleProfileStateInvalid(
                                                UserRole.GUIDE.name()));
        return new RoleAccountContext.Guide(account, GuideProfileSnapshot.from(profile));
    }

    /**
     * {@code requireProvisioned()} + the PARTICIPANT role held + its profile loaded once. See
     * {@link #requireGuide()} for the resolve-once-vs-query-once caveat and the Core-B dependency —
     * the same reasoning applies here, substituting {@code participant_profiles}.
     *
     * @throws ForbiddenException {@code ROLE_REQUIRED} (403) if the account does not hold
     *     PARTICIPANT.
     * @throws ConflictException {@code ROLE_PROFILE_STATE_INVALID} (409) if PARTICIPANT is held but
     *     the participant profile is missing.
     */
    public RoleAccountContext.Participant requireParticipant() {
        ProvisionedAccount account = requireProvisioned();
        requireRoleHeld(account, UserRole.PARTICIPANT);
        var profile =
                participantProfiles
                        .findByUserId(account.userId())
                        .orElseThrow(
                                () ->
                                        ConflictException.roleProfileStateInvalid(
                                                UserRole.PARTICIPANT.name()));
        return new RoleAccountContext.Participant(
                account, ParticipantProfileSnapshot.from(profile));
    }

    /**
     * {@code requireProvisioned()} + {@code nonProfileRole} held, for the profile-less roles
     * (ADMIN/SUPPORT) only.
     *
     * @throws IllegalArgumentException fail-fast if called with GUIDE or PARTICIPANT — those are
     *     profile-backed and MUST go through {@link #requireGuide()} / {@link
     *     #requireParticipant()} instead, so a caller can't bypass the typed, profile-loaded
     *     contexts by routing a profile-backed role through the untyped one.
     * @throws ForbiddenException {@code ROLE_REQUIRED} (403) if the account does not hold {@code
     *     nonProfileRole}.
     */
    public RoleAccountContext.NonProfile requireNonProfileRole(UserRole nonProfileRole) {
        if (nonProfileRole == UserRole.GUIDE || nonProfileRole == UserRole.PARTICIPANT) {
            throw new IllegalArgumentException(
                    "Use requireGuide()/requireParticipant() for profile-backed roles");
        }
        ProvisionedAccount account = requireProvisioned();
        requireRoleHeld(account, nonProfileRole);
        return new RoleAccountContext.NonProfile(account, nonProfileRole);
    }

    private static void requireRoleHeld(ProvisionedAccount account, UserRole role) {
        if (!account.roles().contains(role)) {
            throw new ForbiddenException(
                    "Missing required role: " + role, "ROLE_REQUIRED", Map.of("role", role.name()));
        }
    }

    /**
     * Authorize a role-scoped action against the authoritative role set in {@code user_roles}. Used
     * by future supply-/demand-side endpoints (e.g. guide listings → GUIDE); onboarding endpoints
     * that GRANT a role must NOT call this (they'd 403 the very first acquisition). Authorization
     * always reads {@code user_roles}, never the current role.
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
