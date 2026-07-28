package com.CampusToursLive.domain.onboarding;

import com.CampusToursLive.domain.audit.AuditWriter;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.OnboardingAccountRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.security.OidcIdentity;
import com.CampusToursLive.security.OidcIdentityLock;
import com.CampusToursLive.security.UserProvisioningService;
import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import com.CampusToursLive.web.dto.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single onboarding transaction (CTL-97 Core-B Task 6, spec &sect;5): provision-if-absent +
 * create the role profile + grant the role + write the audit trail, all atomically, under the
 * account's per-identity advisory lock ({@link OidcIdentityLock}).
 *
 * <p>Both {@link #onboardGuide(Jwt, GuideOnboardingRequest)} and {@link #onboardParticipant(Jwt,
 * ParticipantOnboardingRequest)} are the ONLY entry points — each is its own {@code @Transactional}
 * method, and neither calls the other or any other method of this bean that would need the proxy:
 * Task 7's controller only delegates straight into one of these two methods, so the self-invocation
 * trap (an internal call bypassing Spring's transactional proxy) never arises.
 *
 * <p>Fixed error precedence inside the lock (see the class's private helpers): (a) if the account
 * PRE-EXISTED, validate its integrity (roleless, or a role held without its profile — I13); a
 * just-created row is deliberately never integrity-checked (I12) since it is transiently roleless
 * by design; (b) the target role already held &rarr; 409 {@code ROLE_ALREADY_GRANTED}; (c)
 * eligibility for the NEW acquisition (the PARENT&harr;GUIDE exclusion, I13) evaluated on the FRESH
 * locked read &mdash; a missing profile is never treated as "not PARENT" (I9).
 *
 * <p>The response is built directly from the managed {@link UserEntity} plus a fresh post-mutation
 * {@link LockedOnboardingStateReader#loadState(java.util.UUID)} read taken INSIDE this same
 * transaction/lock &mdash; never via Core-A's read-side {@code AccountResolver}, which is the
 * transaction-external path and would not see this not-yet-committed write.
 */
@Service
public class OnboardingService {

    private static final String ACTION_ACCOUNT_PROVISIONED = "ACCOUNT_PROVISIONED";
    private static final String ACTION_ROLE_ACQUIRED = "ROLE_ACQUIRED";
    private static final String TARGET_TYPE_USER = "user";

    private final OidcIdentityLock identityLock;
    private final OnboardingAccountRepository onboardingAccounts;
    private final UserProvisioningService provisioning;
    private final LockedOnboardingStateReader stateReader;
    private final AuditWriter auditWriter;
    private final GuideService guideService;
    private final ParticipantService participantService;

    public OnboardingService(
            OidcIdentityLock identityLock,
            OnboardingAccountRepository onboardingAccounts,
            UserProvisioningService provisioning,
            LockedOnboardingStateReader stateReader,
            AuditWriter auditWriter,
            GuideService guideService,
            ParticipantService participantService) {
        this.identityLock = identityLock;
        this.onboardingAccounts = onboardingAccounts;
        this.provisioning = provisioning;
        this.stateReader = stateReader;
        this.auditWriter = auditWriter;
        this.guideService = guideService;
        this.participantService = participantService;
    }

    @Transactional
    public OnboardingResponse onboardGuide(Jwt jwt, GuideOnboardingRequest req) {
        AccountContext ctx = resolveAccount(jwt);
        LockedOnboardingState state = stateReader.loadState(ctx.user().getId());

        if (!ctx.accountCreated()) {
            validateExistingAccountIntegrity(state);
        }
        if (state.roles().contains(UserRole.GUIDE)) {
            throw ConflictException.roleAlreadyGranted(UserRole.GUIDE.name());
        }
        if (isParentParticipant(state)) {
            throw ConflictException.roleNotEligible(UserRole.GUIDE.name());
        }

        GuideProfileResponse profileResponse =
                guideService.updateProfile(ctx.user(), toGuideUpdateRequest(req));

        LockedOnboardingState postState = stateReader.loadState(ctx.user().getId());
        GuideProfileEntity createdProfile =
                postState
                        .guideProfile()
                        .orElseThrow(
                                () ->
                                        ConflictException.roleProfileStateInvalid(
                                                UserRole.GUIDE.name()));

        recordAudit(
                ctx.accountCreated(),
                postState.user(),
                UserRole.GUIDE,
                createdProfile.getId().toString());

        return buildResponse(postState, UserRole.GUIDE, profileResponse);
    }

    @Transactional
    public OnboardingResponse onboardParticipant(Jwt jwt, ParticipantOnboardingRequest req) {
        AccountContext ctx = resolveAccount(jwt);
        LockedOnboardingState state = stateReader.loadState(ctx.user().getId());

        if (!ctx.accountCreated()) {
            validateExistingAccountIntegrity(state);
        }
        if (state.roles().contains(UserRole.PARTICIPANT)) {
            throw ConflictException.roleAlreadyGranted(UserRole.PARTICIPANT.name());
        }
        if (isParentType(req.participantType()) && state.roles().contains(UserRole.GUIDE)) {
            throw ConflictException.roleNotEligible(UserRole.PARTICIPANT.name());
        }

        ParticipantProfileResponse profileResponse =
                participantService.updateProfile(ctx.user(), toParticipantUpdateRequest(req));

        LockedOnboardingState postState = stateReader.loadState(ctx.user().getId());
        ParticipantProfileEntity createdProfile =
                postState
                        .participantProfile()
                        .orElseThrow(
                                () ->
                                        ConflictException.roleProfileStateInvalid(
                                                UserRole.PARTICIPANT.name()));

        recordAudit(
                ctx.accountCreated(),
                postState.user(),
                UserRole.PARTICIPANT,
                createdProfile.getId().toString());

        return buildResponse(postState, UserRole.PARTICIPANT, profileResponse);
    }

    // ---- steps 2-4: acquire the lock, then find-or-create the account ---------------------

    private record AccountContext(UserEntity user, boolean accountCreated) {}

    private AccountContext resolveAccount(Jwt jwt) {
        identityLock.acquire(new OidcIdentity(jwt.getIssuer().toString(), jwt.getSubject()));

        Optional<UserEntity> existing = onboardingAccounts.findAnyByOidcSubject(jwt.getSubject());
        if (existing.isPresent()) {
            UserEntity user = existing.get();
            if (user.getDeletedAt() != null || user.getAccountStatus() == AccountStatus.DELETED) {
                throw new ForbiddenException("Account is deleted", "ACCOUNT_DELETED");
            }
            if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
                throw new ForbiddenException("Account is suspended", "ACCOUNT_SUSPENDED");
            }
            return new AccountContext(user, false);
        }
        UserEntity created = provisioning.provisionFromJwt(jwt);
        // Flush (not commit) NOW, still inside this same transaction: provisionFromJwt() only
        // persist()s when called nested like this (no outer transaction of its own to trigger an
        // implicit commit-time flush the way it does when CurrentUser calls it standalone).
        // guide_profiles/participant_profiles/user_roles/audit_log all reference user_id via a
        // plain UUID column (not a mapped JPA association), so Hibernate's insert-ordering has no
        // way to infer that dependency on its own -- flushing here pins the users row to the DB
        // FIRST, before any of those FK-dependent inserts are ever queued.
        onboardingAccounts.flush();
        return new AccountContext(created, true);
    }

    // ---- step 6a: existing-account integrity (skipped for a just-created row -- I12) ------

    /**
     * Only ever invoked for an account that already existed before this call ({@code accountCreated
     * == false}). A just-created row is intentionally never run through this: it is transiently
     * roleless by design (I12), which would otherwise trip the roleless check below.
     */
    private void validateExistingAccountIntegrity(LockedOnboardingState state) {
        if (state.roles().isEmpty()) {
            throw ConflictException.accountStateInvalid();
        }
        if (state.roles().contains(UserRole.GUIDE) != state.guideProfile().isPresent()) {
            throw ConflictException.roleProfileStateInvalid(UserRole.GUIDE.name());
        }
        if (state.roles().contains(UserRole.PARTICIPANT)
                != state.participantProfile().isPresent()) {
            throw ConflictException.roleProfileStateInvalid(UserRole.PARTICIPANT.name());
        }
    }

    // ---- step 6c: eligibility for the NEW acquisition (I13, PARENT <-> GUIDE) -------------

    /**
     * A missing participant profile is never "not PARENT" (I9) -- {@link Optional#map} simply
     * short-circuits to {@code false} when the profile is absent, exactly the "not eligible to be
     * excluded" reading this check needs.
     */
    private static boolean isParentParticipant(LockedOnboardingState state) {
        return state.participantProfile()
                .map(p -> p.getParticipantType() == ParticipantType.PARENT)
                .orElse(false);
    }

    private static boolean isParentType(String participantType) {
        return "PARENT".equals(participantType);
    }

    // ---- step 9: in-transaction audit ------------------------------------------------------

    private void recordAudit(
            boolean accountCreated, UserEntity user, UserRole role, String profileId) {
        if (accountCreated) {
            auditWriter.record(
                    ACTION_ACCOUNT_PROVISIONED,
                    TARGET_TYPE_USER,
                    user.getId().toString(),
                    user.getId(),
                    Map.of("accountCreated", true));
        }
        auditWriter.record(
                ACTION_ROLE_ACQUIRED,
                TARGET_TYPE_USER,
                user.getId().toString(),
                user.getId(),
                Map.of(
                        "role", role.name(),
                        "profileId", profileId,
                        "accountCreated", accountCreated));
    }

    // ---- step 10: response snapshot, built directly from the managed user + fresh state ---

    private static OnboardingResponse buildResponse(
            LockedOnboardingState state, UserRole acquiredRole, Object profile) {
        return new OnboardingResponse(
                state.user().getAccountStatus(),
                UserSummary.of(state.user()),
                rolesInFixedOrder(state.roles()),
                acquiredRole,
                profile);
    }

    /** PARTICIPANT, GUIDE, ADMIN, SUPPORT -- {@link UserRole}'s own declaration order. */
    private static List<UserRole> rolesInFixedOrder(Set<UserRole> roles) {
        List<UserRole> ordered = new ArrayList<>();
        for (UserRole role : UserRole.values()) {
            if (roles.contains(role)) {
                ordered.add(role);
            }
        }
        return ordered;
    }

    // ---- request mapping: onboarding COMMAND DTOs -> the existing update DTOs -------------

    private static GuideProfileUpdateRequest toGuideUpdateRequest(GuideOnboardingRequest req) {
        return new GuideProfileUpdateRequest(
                req.firstName(),
                req.lastName(),
                req.universityId(),
                req.major(),
                req.classYear(),
                req.bio(),
                req.spokenLanguages(),
                req.tourTopics(),
                req.verificationEmail(),
                true,
                req.degree(),
                req.entryYear());
    }

    private static ParticipantProfileUpdateRequest toParticipantUpdateRequest(
            ParticipantOnboardingRequest req) {
        return new ParticipantProfileUpdateRequest(
                req.firstName(),
                req.lastName(),
                req.displayName(),
                req.participantType(),
                req.gradeLevel(),
                req.intendedMajor(),
                req.universitiesOfInterest(),
                req.topicsOfInterest(),
                req.preferredLanguage(),
                req.timezone(),
                req.accessibilityPreferences());
    }
}
