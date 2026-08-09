package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.user.AccountProjection;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * AccountResolver.resolveAuthenticatedIdentity — the ONE read-only classification of an
 * authenticated identity against a single {@link AccountProjection} snapshot (mocked here; the real
 * native query is proven separately by an integration test against Testcontainers Postgres). Covers
 * every branch: no row, deny-safe deletion (including signals that disagree), suspension, empty
 * roles, exactly-one-profile enforcement per profile-backed role (0 / 1 / 2), orphan profiles, and
 * profile-less roles (ADMIN/SUPPORT).
 */
@ExtendWith(MockitoExtension.class)
class AccountResolverTest {

    private static final String SUBJECT = "sub-1";
    private static final Instant CREATED_AT = Instant.parse("2024-01-01T00:00:00Z");

    @Mock UserRepository users;

    private AccountResolver resolver() {
        return new AccountResolver(users);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
    }

    /**
     * A healthy, role-less ACTIVE account with zero profiles; each test overrides what it needs.
     */
    private static AccountProjection defaultProjection() {
        AccountProjection p = mock(AccountProjection.class);
        lenient().when(p.getId()).thenReturn(java.util.UUID.randomUUID());
        lenient().when(p.getOidcSubject()).thenReturn(SUBJECT);
        lenient().when(p.getEmail()).thenReturn("ada@example.com");
        lenient().when(p.getFirstName()).thenReturn("Ada");
        lenient().when(p.getLastName()).thenReturn("Lovelace");
        lenient().when(p.getDisplayName()).thenReturn("Ada Lovelace");
        lenient().when(p.getAccountStatus()).thenReturn(AccountStatus.ACTIVE.name());
        lenient().when(p.getAgeBand()).thenReturn(AgeBand.ADULT.name());
        lenient().when(p.getCreatedAt()).thenReturn(CREATED_AT);
        lenient().when(p.getDeletedAt()).thenReturn(null);
        lenient().when(p.getGuideRole()).thenReturn(false);
        lenient().when(p.getParticipantRole()).thenReturn(false);
        lenient().when(p.getAdminRole()).thenReturn(false);
        lenient().when(p.getSupportRole()).thenReturn(false);
        lenient().when(p.getGuideProfileCount()).thenReturn(0L);
        lenient().when(p.getParticipantProfileCount()).thenReturn(0L);
        lenient().when(p.getParticipantTypeCount()).thenReturn(0L);
        lenient().when(p.getParticipantType()).thenReturn(null);
        return p;
    }

    private void stub(AccountProjection projection) {
        when(users.findAccountProjectionByOidcSubject(SUBJECT)).thenReturn(Optional.of(projection));
    }

    // ---- no row ---------------------------------------------------------------------------

    @Test
    void resolve_returnsPending_whenNoRow() {
        when(users.findAccountProjectionByOidcSubject(SUBJECT)).thenReturn(Optional.empty());

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.Pending.class);
    }

    // ---- deny-safe deletion -----------------------------------------------------------------

    @Test
    void resolve_returnsDeleted_whenDeletedAtSet_evenIfStatusStillActive() {
        AccountProjection p = defaultProjection();
        when(p.getDeletedAt()).thenReturn(Instant.now());
        stub(p);

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.Deleted.class);
    }

    @Test
    void resolve_returnsDeleted_whenStatusDeleted_evenIfDeletedAtNull() {
        AccountProjection p = defaultProjection();
        when(p.getAccountStatus()).thenReturn(AccountStatus.DELETED.name());
        stub(p);

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.Deleted.class);
    }

    @Test
    void resolve_returnsDeleted_whenBothSignalsAgree() {
        AccountProjection p = defaultProjection();
        when(p.getDeletedAt()).thenReturn(Instant.now());
        when(p.getAccountStatus()).thenReturn(AccountStatus.DELETED.name());
        stub(p);

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.Deleted.class);
    }

    // ---- suspension -------------------------------------------------------------------------

    @Test
    void resolve_returnsSuspended_whenStatusSuspended() {
        AccountProjection p = defaultProjection();
        when(p.getAccountStatus()).thenReturn(AccountStatus.SUSPENDED.name());
        stub(p);

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.Suspended.class);
    }

    // ---- empty roles ------------------------------------------------------------------------

    @Test
    void resolve_returnsAccountStateInvalid_whenActiveWithNoRoles() {
        stub(defaultProjection());

        assertThat(resolver().resolveAuthenticatedIdentity(jwt()))
                .isInstanceOf(AccountResolution.AccountStateInvalid.class);
    }

    // ---- GUIDE: exactly-one-profile enforcement --------------------------------------------

    @Test
    void resolve_returnsProvisioned_whenGuideWithExactlyOneProfile() {
        AccountProjection p = defaultProjection();
        when(p.getGuideRole()).thenReturn(true);
        when(p.getGuideProfileCount()).thenReturn(1L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        ProvisionedAccount account = ((AccountResolution.Provisioned) result).account();
        assertThat(account.roles()).containsExactlyInAnyOrder(UserRole.GUIDE);
    }

    @Test
    void resolve_returnsRoleProfileStateInvalid_whenGuideProfileCountZero() {
        AccountProjection p = defaultProjection();
        when(p.getGuideRole()).thenReturn(true);
        when(p.getGuideProfileCount()).thenReturn(0L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.RoleProfileStateInvalid.class);
        assertThat(((AccountResolution.RoleProfileStateInvalid) result).role())
                .isEqualTo(UserRole.GUIDE);
        assertThat(((AccountResolution.Invalid) result).code())
                .isEqualTo("ROLE_PROFILE_STATE_INVALID");
    }

    @Test
    void resolve_returnsRoleProfileStateInvalid_whenGuideProfileCountTwo() {
        // exactly-one, not mere existence: a duplicated profile row is still invalid.
        AccountProjection p = defaultProjection();
        when(p.getGuideRole()).thenReturn(true);
        when(p.getGuideProfileCount()).thenReturn(2L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.RoleProfileStateInvalid.class);
        assertThat(((AccountResolution.RoleProfileStateInvalid) result).role())
                .isEqualTo(UserRole.GUIDE);
    }

    // ---- PARTICIPANT: exactly-one-profile enforcement --------------------------------------

    @Test
    void resolve_returnsRoleProfileStateInvalid_whenParticipantProfileCountZero() {
        AccountProjection p = defaultProjection();
        when(p.getParticipantRole()).thenReturn(true);
        when(p.getParticipantProfileCount()).thenReturn(0L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.RoleProfileStateInvalid.class);
        assertThat(((AccountResolution.RoleProfileStateInvalid) result).role())
                .isEqualTo(UserRole.PARTICIPANT);
    }

    @Test
    void resolve_returnsProvisioned_whenParticipantWithExactlyOneProfile() {
        AccountProjection p = defaultProjection();
        when(p.getParticipantRole()).thenReturn(true);
        when(p.getParticipantProfileCount()).thenReturn(1L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        assertThat(((AccountResolution.Provisioned) result).account().roles())
                .containsExactlyInAnyOrder(UserRole.PARTICIPANT);
    }

    // ---- orphan profiles (profile row present, role NOT held) ------------------------------

    @Test
    void resolve_returnsRoleProfileStateInvalid_whenOrphanGuideProfile() {
        AccountProjection p = defaultProjection();
        // Account legitimately holds ADMIN (so roles isn't empty), but GUIDE role is NOT held
        // even though a guide_profiles row exists — an orphan profile, a data-integrity problem.
        when(p.getAdminRole()).thenReturn(true);
        when(p.getGuideProfileCount()).thenReturn(1L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.RoleProfileStateInvalid.class);
        assertThat(((AccountResolution.RoleProfileStateInvalid) result).role())
                .isEqualTo(UserRole.GUIDE);
    }

    @Test
    void resolve_returnsRoleProfileStateInvalid_whenOrphanParticipantProfile() {
        AccountProjection p = defaultProjection();
        when(p.getAdminRole()).thenReturn(true);
        when(p.getParticipantProfileCount()).thenReturn(1L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.RoleProfileStateInvalid.class);
        assertThat(((AccountResolution.RoleProfileStateInvalid) result).role())
                .isEqualTo(UserRole.PARTICIPANT);
    }

    // ---- profile-less roles (ADMIN / SUPPORT) ----------------------------------------------

    @Test
    void resolve_returnsProvisioned_whenAdminOnly_noProfileExpected() {
        AccountProjection p = defaultProjection();
        when(p.getAdminRole()).thenReturn(true);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        assertThat(((AccountResolution.Provisioned) result).account().roles())
                .containsExactlyInAnyOrder(UserRole.ADMIN);
    }

    @Test
    void resolve_returnsProvisioned_whenSupportOnly_noProfileExpected() {
        AccountProjection p = defaultProjection();
        when(p.getSupportRole()).thenReturn(true);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        assertThat(((AccountResolution.Provisioned) result).account().roles())
                .containsExactlyInAnyOrder(UserRole.SUPPORT);
    }

    // ---- multi-role: both profile-backed roles valid at once ------------------------------

    @Test
    void resolve_returnsProvisioned_whenGuideAndParticipantBothHeldWithOneProfileEach() {
        AccountProjection p = defaultProjection();
        when(p.getGuideRole()).thenReturn(true);
        when(p.getGuideProfileCount()).thenReturn(1L);
        when(p.getParticipantRole()).thenReturn(true);
        when(p.getParticipantProfileCount()).thenReturn(1L);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(result).isInstanceOf(AccountResolution.Provisioned.class);
        assertThat(((AccountResolution.Provisioned) result).account().roles())
                .containsExactlyInAnyOrder(UserRole.GUIDE, UserRole.PARTICIPANT);
    }

    // ---- ProvisionedAccount is built from projection fields, never a UserEntity ------------

    @Test
    void resolve_buildsProvisionedAccount_fromProjectionFields() {
        AccountProjection p = defaultProjection();
        when(p.getAdminRole()).thenReturn(true);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        ProvisionedAccount account = ((AccountResolution.Provisioned) result).account();
        assertThat(account.userId()).isEqualTo(p.getId());
        assertThat(account.oidcSubject()).isEqualTo(SUBJECT);
        assertThat(account.email()).isEqualTo("ada@example.com");
        assertThat(account.firstName()).isEqualTo("Ada");
        assertThat(account.lastName()).isEqualTo("Lovelace");
        assertThat(account.displayName()).isEqualTo("Ada Lovelace");
        assertThat(account.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.ageBand()).isEqualTo(AgeBand.ADULT);
        assertThat(account.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void resolve_toleratesNullAgeBand() {
        AccountProjection p = defaultProjection();
        when(p.getAdminRole()).thenReturn(true);
        when(p.getAgeBand()).thenReturn(null);
        stub(p);

        AccountResolution result = resolver().resolveAuthenticatedIdentity(jwt());

        assertThat(((AccountResolution.Provisioned) result).account().ageBand()).isNull();
    }

    // ---- single-snapshot: exactly ONE repository call, no separate role/profile lookups ----

    @Test
    void resolve_issuesExactlyOneRepositoryQuery() {
        AccountProjection p = defaultProjection();
        when(p.getAdminRole()).thenReturn(true);
        stub(p);

        resolver().resolveAuthenticatedIdentity(jwt());

        verify(users).findAccountProjectionByOidcSubject(SUBJECT);
        verifyNoMoreInteractions(users);
    }
}
