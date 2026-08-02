package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.AccountProjection;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Classifies an authenticated identity (a validated {@code Jwt}) into an {@link AccountResolution}
 * using ONE read-only database snapshot — see {@code
 * UserRepository#findAccountProjectionByOidcSubject}.
 *
 * <p>This resolver never provisions and never constructs a {@code UserEntity}: it reads the {@link
 * AccountProjection} row, classifies it, and — for a healthy account — builds an immutable {@link
 * ProvisionedAccount} straight from the projection's own fields. Everything downstream of "what
 * should happen to a non-{@code Provisioned} result" (401, 403, 409 coded problem, …) is a later
 * task's concern, not this one's.
 */
@Component
public class AccountResolver {

    private static final Logger log = LoggerFactory.getLogger(AccountResolver.class);

    private final UserRepository users;

    public AccountResolver(UserRepository users) {
        this.users = users;
    }

    /**
     * Read-only: classifies the subject in the JWT against a single DB snapshot. Never provisions.
     */
    public AccountResolution resolveAuthenticatedIdentity(Jwt jwt) {
        return users.findAccountProjectionByOidcSubject(jwt.getSubject())
                .map(this::classify)
                .orElseGet(AccountResolution.Pending::new);
    }

    private AccountResolution classify(AccountProjection projection) {
        boolean deletedAtSet = projection.getDeletedAt() != null;
        boolean statusDeleted = AccountStatus.DELETED.name().equals(projection.getAccountStatus());
        if (deletedAtSet || statusDeleted) {
            // Deny-safe: either signal alone is enough to deny. This is NOT an assertion that the
            // two signals agree — log telemetry so a mismatch (e.g. deleted_at set but
            // account_status still ACTIVE) is observable without blocking the deny.
            if (deletedAtSet != statusDeleted) {
                log.warn(
                        "Deletion signals disagree for user {}: deletedAt={}, accountStatus={}",
                        projection.getId(),
                        projection.getDeletedAt(),
                        projection.getAccountStatus());
            }
            return new AccountResolution.Deleted();
        }

        if (AccountStatus.SUSPENDED.name().equals(projection.getAccountStatus())) {
            return new AccountResolution.Suspended();
        }

        Set<UserRole> roles = new LinkedHashSet<>();
        if (projection.getGuideRole()) {
            roles.add(UserRole.GUIDE);
        }
        if (projection.getParticipantRole()) {
            roles.add(UserRole.PARTICIPANT);
        }
        if (projection.getAdminRole()) {
            roles.add(UserRole.ADMIN);
        }
        if (projection.getSupportRole()) {
            roles.add(UserRole.SUPPORT);
        }

        if (roles.isEmpty()) {
            return new AccountResolution.AccountStateInvalid();
        }

        AccountResolution.Invalid roleProfileProblem = checkRoleProfilePairing(projection, roles);
        if (roleProfileProblem != null) {
            return roleProfileProblem;
        }

        return new AccountResolution.Provisioned(toProvisionedAccount(projection, roles));
    }

    /**
     * For each profile-backed role (GUIDE, PARTICIPANT): held ⇒ exactly one profile row; not held ⇒
     * zero profile rows (a nonzero count for a role not held is an orphan profile). ADMIN/SUPPORT
     * have no profile table, so they are never checked here.
     */
    private AccountResolution.Invalid checkRoleProfilePairing(
            AccountProjection projection, Set<UserRole> roles) {
        if (roles.contains(UserRole.GUIDE)) {
            if (projection.getGuideProfileCount() != 1) {
                return new AccountResolution.RoleProfileStateInvalid(UserRole.GUIDE);
            }
        } else if (projection.getGuideProfileCount() > 0) {
            return new AccountResolution.RoleProfileStateInvalid(UserRole.GUIDE);
        }

        if (roles.contains(UserRole.PARTICIPANT)) {
            if (projection.getParticipantProfileCount() != 1) {
                return new AccountResolution.RoleProfileStateInvalid(UserRole.PARTICIPANT);
            }
        } else if (projection.getParticipantProfileCount() > 0) {
            return new AccountResolution.RoleProfileStateInvalid(UserRole.PARTICIPANT);
        }

        return null;
    }

    private ProvisionedAccount toProvisionedAccount(
            AccountProjection projection, Set<UserRole> roles) {
        String ageBand = projection.getAgeBand();
        return new ProvisionedAccount(
                projection.getId(),
                projection.getOidcSubject(),
                projection.getEmail(),
                projection.getFirstName(),
                projection.getLastName(),
                projection.getDisplayName(),
                AccountStatus.valueOf(projection.getAccountStatus()),
                ageBand == null ? null : AgeBand.valueOf(ageBand),
                projection.getCreatedAt(),
                roles);
    }
}
