package com.CampusToursLive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * CurrentUser.requireProvisioned / requireGuide / requireParticipant / requireNonProfileRole — the
 * typed authorization helpers built on top of {@link AccountResolver#resolveAuthenticatedIdentity},
 * covering every {@link AccountResolution} outcome and the role/profile gating layered on top of
 * it.
 *
 * <p>Note: the produced {@code RoleAccountContext.NonProfile requireRole(UserRole)} signature from
 * the plan collides with the pre-existing, actively-used {@code requireRole(UserRole)} (returns a
 * bare {@code UserEntity}, still called by ~28 controller sites for GUIDE/PARTICIPANT) — Java can't
 * overload on return type alone. The new typed, fail-fast method is implemented here as {@link
 * CurrentUser#requireNonProfileRole(UserRole)} instead. {@code requireRole(UserRole)} itself was
 * reimplemented on top of {@link CurrentUser#requireProvisioned()} in Task 6 (see {@link
 * CurrentUserTest}) — kept as the untyped {@code UserEntity} gate deliberately, since migrating its
 * ~28 call sites to the typed contexts is out of Core-A's scope.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserAuthzTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SUBJECT = "sub-1";

    @Mock UserRepository users;
    @Mock UserProvisioningService provisioning;
    @Mock AccountResolver accountResolver;
    @Mock GuideProfileRepository guideProfiles;
    @Mock ParticipantProfileRepository participantProfiles;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                users, provisioning, accountResolver, guideProfiles, participantProfiles);
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    private void stubResolution(AccountResolution resolution) {
        when(accountResolver.resolveAuthenticatedIdentity(any())).thenReturn(resolution);
        authenticate();
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

    // ---- requireProvisioned(): every AccountResolution branch --------------------------------

    @Test
    void requireProvisioned_returnsAccount_whenProvisioned() {
        ProvisionedAccount account = provisionedAccount(UserRole.ADMIN);
        stubResolution(new AccountResolution.Provisioned(account));

        assertThat(currentUser().requireProvisioned()).isSameAs(account);
    }

    @Test
    void requireProvisioned_throws404_withCode_whenPending() {
        stubResolution(new AccountResolution.Pending());

        assertThatThrownBy(() -> currentUser().requireProvisioned())
                .isInstanceOf(NotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((NotFoundException) ex).code())
                                        .isEqualTo("ACCOUNT_NOT_PROVISIONED"));
    }

    @Test
    void requireProvisioned_throws403_withCode_whenSuspended() {
        stubResolution(new AccountResolution.Suspended());

        assertThatThrownBy(() -> currentUser().requireProvisioned())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ACCOUNT_SUSPENDED"));
    }

    @Test
    void requireProvisioned_throws403_withCode_whenDeleted() {
        stubResolution(new AccountResolution.Deleted());

        assertThatThrownBy(() -> currentUser().requireProvisioned())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ACCOUNT_DELETED"));
    }

    @Test
    void requireProvisioned_throws409_withCode_whenAccountStateInvalid() {
        stubResolution(new AccountResolution.AccountStateInvalid());

        assertThatThrownBy(() -> currentUser().requireProvisioned())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ACCOUNT_STATE_INVALID"));
    }

    @Test
    void requireProvisioned_throws409_withCode_whenRoleProfileStateInvalid() {
        stubResolution(new AccountResolution.RoleProfileStateInvalid(UserRole.GUIDE));

        assertThatThrownBy(() -> currentUser().requireProvisioned())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex -> {
                            ConflictException conflict = (ConflictException) ex;
                            assertThat(conflict.code()).isEqualTo("ROLE_PROFILE_STATE_INVALID");
                            assertThat(conflict.properties()).containsEntry("role", "GUIDE");
                        });
    }

    // ---- requireGuide() ----------------------------------------------------------------------

    @Test
    void requireGuide_returnsTypedContext_whenRoleHeldAndProfilePresent() {
        ProvisionedAccount account = provisionedAccount(UserRole.GUIDE);
        stubResolution(new AccountResolution.Provisioned(account));
        GuideProfileEntity entity = new GuideProfileEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER_ID);
        entity.setBio("Hi");
        when(guideProfiles.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        RoleAccountContext.Guide ctx = currentUser().requireGuide();

        assertThat(ctx.account()).isSameAs(account);
        assertThat(ctx.profile().userId()).isEqualTo(USER_ID);
        assertThat(ctx.profile().bio()).isEqualTo("Hi");
    }

    @Test
    void requireGuide_throws403_roleRequired_whenGuideNotHeld() {
        stubResolution(new AccountResolution.Provisioned(provisionedAccount(UserRole.PARTICIPANT)));

        assertThatThrownBy(() -> currentUser().requireGuide())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
        verify(guideProfiles, never()).findByUserId(any());
    }

    @Test
    void requireGuide_throws409_roleProfileStateInvalid_notProfileNotFound_whenProfileMissing() {
        stubResolution(new AccountResolution.Provisioned(provisionedAccount(UserRole.GUIDE)));
        when(guideProfiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUser().requireGuide())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex -> {
                            ConflictException conflict = (ConflictException) ex;
                            assertThat(conflict.code()).isEqualTo("ROLE_PROFILE_STATE_INVALID");
                            assertThat(conflict.properties()).containsEntry("role", "GUIDE");
                        });
    }

    // ---- requireParticipant() -----------------------------------------------------------------

    @Test
    void requireParticipant_returnsTypedContext_whenRoleHeldAndProfilePresent() {
        ProvisionedAccount account = provisionedAccount(UserRole.PARTICIPANT);
        stubResolution(new AccountResolution.Provisioned(account));
        ParticipantProfileEntity entity = new ParticipantProfileEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER_ID);
        when(participantProfiles.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        RoleAccountContext.Participant ctx = currentUser().requireParticipant();

        assertThat(ctx.account()).isSameAs(account);
        assertThat(ctx.profile().userId()).isEqualTo(USER_ID);
    }

    @Test
    void requireParticipant_throws403_roleRequired_whenParticipantNotHeld() {
        stubResolution(new AccountResolution.Provisioned(provisionedAccount(UserRole.GUIDE)));

        assertThatThrownBy(() -> currentUser().requireParticipant())
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
        verify(participantProfiles, never()).findByUserId(any());
    }

    @Test
    void requireParticipant_throws409_roleProfileStateInvalid_whenProfileMissing() {
        stubResolution(new AccountResolution.Provisioned(provisionedAccount(UserRole.PARTICIPANT)));
        when(participantProfiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUser().requireParticipant())
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex -> {
                            ConflictException conflict = (ConflictException) ex;
                            assertThat(conflict.code()).isEqualTo("ROLE_PROFILE_STATE_INVALID");
                            assertThat(conflict.properties()).containsEntry("role", "PARTICIPANT");
                        });
    }

    // ---- requireNonProfileRole(): ADMIN/SUPPORT + fail-fast guard -----------------------------

    @Test
    void requireNonProfileRole_returnsTypedContext_whenRoleHeld() {
        ProvisionedAccount account = provisionedAccount(UserRole.ADMIN);
        stubResolution(new AccountResolution.Provisioned(account));

        RoleAccountContext.NonProfile ctx = currentUser().requireNonProfileRole(UserRole.ADMIN);

        assertThat(ctx.account()).isSameAs(account);
        assertThat(ctx.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void requireNonProfileRole_throws403_roleRequired_whenRoleNotHeld() {
        stubResolution(new AccountResolution.Provisioned(provisionedAccount(UserRole.SUPPORT)));

        assertThatThrownBy(() -> currentUser().requireNonProfileRole(UserRole.ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(
                        ex ->
                                assertThat(((ForbiddenException) ex).code())
                                        .isEqualTo("ROLE_REQUIRED"));
    }

    @Test
    void requireNonProfileRole_failsFast_whenCalledWithGuide() {
        // No stubbing of the resolver/context: the guard must reject before any DB read.
        assertThatThrownBy(() -> currentUser().requireNonProfileRole(UserRole.GUIDE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requireGuide");
    }

    @Test
    void requireNonProfileRole_failsFast_whenCalledWithParticipant() {
        assertThatThrownBy(() -> currentUser().requireNonProfileRole(UserRole.PARTICIPANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requireParticipant");
    }
}
