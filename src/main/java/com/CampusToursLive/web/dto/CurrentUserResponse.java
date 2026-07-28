package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.ProvisionedAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

/**
 * Current principal (GET /users/me, POST /session). Deliberately NOT named {@code MeResponse} to
 * avoid a Core-vs-bff name collision — the bff owns a distinct, session-scoped {@code /userinfo}
 * that additionally carries {@code currentRole}. Core knows nothing about current/session role.
 */
@Schema(
        name = "CurrentUserResponse",
        description =
                "The authenticated principal: account identity and the authoritative role set. No"
                        + " session/current-role context — that is owned by the BFF session, not"
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

    /**
     * Built straight from a {@link ProvisionedAccount} snapshot — no re-query. The snapshot's
     * {@code roles()} is a {@code Set} in role-check insertion order (see {@code
     * AccountResolver#classify}), not the fixed enum order this response promises, so it is
     * re-sorted here the same way {@code UserRoleEntity}-derived roles are elsewhere.
     */
    public static CurrentUserResponse of(ProvisionedAccount account) {
        List<UserRole> roles =
                account.roles().stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        return new CurrentUserResponse(UserSummary.of(account), roles);
    }
}
