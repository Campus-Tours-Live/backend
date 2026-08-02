package com.CampusToursLive.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Write-side, lifecycle-inclusive identity lookup for Core-B's onboarding flow — CTL-97 Core-B Task
 * 2.
 *
 * <p>Contrast with {@link com.CampusToursLive.security.AccountResolver}, the READ-side
 * single-snapshot classifier used to answer "is this JWT's subject a healthy, provisioned account
 * right now": {@code AccountResolver} treats SUSPENDED/DELETED as terminal verdicts ({@code
 * Suspended}/{@code Deleted}) to report, not as rows for the caller to act on.
 *
 * <p>{@link #findAnyByOidcSubject(String)} is the WRITE-side counterpart: onboarding needs to find
 * the underlying {@link UserEntity} row for a subject regardless of lifecycle state — including
 * SUSPENDED and soft-deleted ({@code deletedAt} set / {@code accountStatus == DELETED}) — because a
 * write-side decision (e.g. "does this oidc_subject already have a row, even a deleted one, that
 * onboarding must reconcile against") needs to see every row, not only the ones a resolver would
 * classify as healthy. {@link UserEntity} carries no {@code @Where} soft-delete filter, so a plain
 * lookup already returns every lifecycle state; this repository exists as a distinctly-typed,
 * purpose-scoped home for that lookup so {@code ArchitectureGuardTest} can allowlist it BY TYPE as
 * a legitimate exception to the "no ad hoc {@code findByOidcSubject}" rule, instead of either (a)
 * growing {@link UserRepository#findByOidcSubject(String)}'s own allowlist, or (b) reusing that
 * raw, single-purpose method outside {@code CurrentUser}/{@code AccountResolver}.
 */
public interface OnboardingAccountRepository extends JpaRepository<UserEntity, UUID> {

    /** Lifecycle-inclusive: returns a row in ANY {@code account_status}, deleted or not. */
    Optional<UserEntity> findAnyByOidcSubject(String oidcSubject);
}
