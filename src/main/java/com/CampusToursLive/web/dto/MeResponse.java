package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Current principal (matches openapi MeEnvelope.data). */
@Schema(
        name = "MeResponse",
        description = "The authenticated principal: identity and the authoritative role set.")
public record MeResponse(
        @Schema(
                        description = "Account-level identity.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UserSummary user,
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
                String activeRole) {

    public static MeResponse of(UserEntity u, List<String> roles) {
        return new MeResponse(
                UserSummary.of(u),
                roles,
                u.getLastActiveRole() != null ? u.getLastActiveRole().name() : null);
    }
}
