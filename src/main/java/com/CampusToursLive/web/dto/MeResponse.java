package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import java.util.List;

/** Current principal (matches openapi MeEnvelope.data). */
public record MeResponse(
        String id,
        List<String> roles, // authoritative role set (user_roles)
        String activeRole, // = users.last_active_role (UX context, never authorization)
        String participantType, // from participant_profile (null if no participant role)
        String guideStatus, // = guide_profile.application_status (null if no guide profile)
        String firstName,
        String lastName,
        String displayName,
        String email,
        String accountStatus,
        String ageBand,
        String createdAt) { // ISO-8601 UTC instant of account creation (the "member since" date)

    public static MeResponse of(
            UserEntity u, List<String> roles, String participantType, String guideStatus) {
        return new MeResponse(
                u.getId().toString(),
                roles,
                u.getLastActiveRole() != null ? u.getLastActiveRole().name() : null,
                participantType,
                guideStatus,
                u.getFirstName(),
                u.getLastName(),
                u.getDisplayName(),
                u.getEmail(),
                u.getAccountStatus() != null ? u.getAccountStatus().name() : null,
                u.getAgeBand() != null ? u.getAgeBand().name() : null,
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
    }
}
