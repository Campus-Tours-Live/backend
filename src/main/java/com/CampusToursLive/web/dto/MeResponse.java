package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Current principal (matches openapi MeEnvelope.data). */
@Schema(
        name = "MeResponse",
        description =
                "The authenticated principal: identity, the authoritative role set, and per-role"
                        + " status.")
public record MeResponse(
        @Schema(
                        description = "User id (UUID).",
                        example = "22222222-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description =
                                "Authoritative role set from user_roles — the basis for all"
                                        + " authorization.",
                        example = "[\"PARTICIPANT\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> roles,
        @Schema(
                        description =
                                "The caller's active role (UX context only, never authorization);"
                                        + " null if unset.",
                        example = "PARTICIPANT",
                        allowableValues = {"PARTICIPANT", "GUIDE"},
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String activeRole,
        @Schema(
                        description = "Participant type; null if the user has no participant role.",
                        example = "PROSPECTIVE_STUDENT",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String participantType,
        @Schema(
                        description =
                                "Guide application status; null if the user has no guide profile.",
                        example = "APPROVED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String guideStatus,
        @Schema(
                        description = "First name.",
                        example = "Sam",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String firstName,
        @Schema(
                        description = "Last name.",
                        example = "Rivera",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String lastName,
        @Schema(
                        description = "Public display name.",
                        example = "Sam Rivera",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String displayName,
        @Schema(
                        description = "Account email.",
                        example = "sam.rivera@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        @Schema(
                        description = "Account lifecycle status.",
                        example = "ACTIVE",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String accountStatus,
        @Schema(
                        description = "Coarse age band derived at signup.",
                        example = "ADULT",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String ageBand,
        @Schema(
                        description =
                                "ISO-8601 UTC instant of account creation (the \"member since\""
                                        + " date).",
                        example = "2026-01-15T09:30:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String createdAt) {

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
