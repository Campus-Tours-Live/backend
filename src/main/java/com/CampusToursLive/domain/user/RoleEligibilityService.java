package com.CampusToursLive.domain.user;

import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.web.dto.RoleEligibilityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoritative "can this account acquire this role" check, backing {@code GET
 * /users/me/role-eligibility}. Replaces the removed {@code /userinfo.participantType} inspection —
 * the bff routes signup/onboarding on this response rather than reading a profile field itself.
 *
 * <p>Rule order: a disabled/suspended account is whole-account (not role-specific), so it is
 * rejected before any role-specific check — see {@link #checkEligibility}. Already holding the role
 * is checked defensively (the caller's own "holds requestedRole" branch normally short-circuits
 * first). GUIDE is additionally denied to a PARENT-type participant.
 */
@Service
public class RoleEligibilityService {

    private final UserRoleRepository userRoles;
    private final ParticipantProfileRepository participantProfiles;

    public RoleEligibilityService(
            UserRoleRepository userRoles, ParticipantProfileRepository participantProfiles) {
        this.userRoles = userRoles;
        this.participantProfiles = participantProfiles;
    }

    /**
     * @throws ForbiddenException {@code ACCOUNT_NOT_ACTIVE} when the account is not ACTIVE — a
     *     whole-account condition, so it is a 403, never {@code eligible=false}.
     */
    @Transactional(readOnly = true)
    public RoleEligibilityResponse checkEligibility(UserEntity user, UserRole role) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("ACCOUNT_NOT_ACTIVE");
        }
        if (userRoles.existsByUserIdAndRole(user.getId(), role)) {
            return new RoleEligibilityResponse(false, RoleIneligibilityReason.ROLE_ALREADY_HELD);
        }
        if (role == UserRole.GUIDE && isParentParticipant(user)) {
            return new RoleEligibilityResponse(
                    false, RoleIneligibilityReason.PARENT_CANNOT_BECOME_GUIDE);
        }
        return new RoleEligibilityResponse(true, null);
    }

    private boolean isParentParticipant(UserEntity user) {
        return participantProfiles
                .findByUserId(user.getId())
                .map(p -> p.getParticipantType() == ParticipantType.PARENT)
                .orElse(false);
    }
}
