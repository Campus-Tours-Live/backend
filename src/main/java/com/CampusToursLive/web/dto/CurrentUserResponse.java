package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Current principal (GET /users/me, POST /session). Deliberately NOT named {@code MeResponse} to
 * avoid a Core-vs-bff name collision — the bff owns a distinct, session-scoped {@code /userinfo}
 * that additionally carries {@code activeRole}. Core knows nothing about active/session role.
 */
@Schema(
        name = "CurrentUserResponse",
        description =
                "The authenticated principal: account identity and the authoritative role set. No"
                        + " session/active-role context — that is owned by the BFF session, not"
                        + " Core.")
public record CurrentUserResponse(
        @Schema(
                        description = "Account-level identity.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UserSummary user,
        @Schema(
                        description =
                                "Authoritative role set from user_roles — the basis for all"
                                        + " authorization. Listed in a fixed enum order"
                                        + " (PARTICIPANT, GUIDE, ADMIN, SUPPORT), not insertion"
                                        + " order, so responses are stable.",
                        example = "[\"PARTICIPANT\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<UserRole> roles) {

    public static CurrentUserResponse of(UserEntity u, List<UserRole> roles) {
        return new CurrentUserResponse(UserSummary.of(u), roles);
    }
}
