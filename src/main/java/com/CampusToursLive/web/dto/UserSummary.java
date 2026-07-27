package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/** Account-level identity (matches openapi CurrentUserResponse.user). No role-scoped fields. */
@Schema(
        name = "UserSummary",
        description = "Account-level identity: the user's own record, independent of any role.")
public record UserSummary(
        @Schema(
                        description = "User id (UUID).",
                        example = "22222222-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
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

    public static UserSummary of(UserEntity u) {
        return new UserSummary(
                u.getId().toString(),
                u.getFirstName(),
                u.getLastName(),
                u.getDisplayName(),
                u.getEmail(),
                u.getAccountStatus() != null ? u.getAccountStatus().name() : null,
                u.getAgeBand() != null ? u.getAgeBand().name() : null,
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
    }
}
