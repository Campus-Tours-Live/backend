package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response body for the onboarding COMMAND endpoints (Task 7's controller — not wired yet): creates
 * a full role + profile in one call and returns the resulting account snapshot. Deliberately has NO
 * {@code currentRole} — unlike the bff's session-scoped {@code /userinfo}, Core knows nothing about
 * current/session role (see {@link CurrentUserResponse}'s own note). {@code profile} holds the
 * newly created role's profile view — a {@link GuideProfileResponse} when {@code acquiredRole} is
 * GUIDE, or a {@link ParticipantProfileResponse} when it is PARTICIPANT — typed {@code Object}
 * since the two are unrelated shapes (a union, not a common supertype).
 */
@Schema(
        name = "OnboardingResponse",
        description =
                "Result of an onboarding command: the account snapshot right after the role +"
                        + " profile were created in one call. No currentRole — that is bff-owned.")
public record OnboardingResponse(
        @Schema(
                        description =
                                "Account-level identity (includes the account lifecycle status as"
                                        + " user.accountStatus — there is no separate top-level"
                                        + " lifecycle field).",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UserSummary user,
        @Schema(
                        description =
                                "Authoritative role set from user_roles after this command's grant,"
                                        + " listed in a fixed enum order (PARTICIPANT, GUIDE,"
                                        + " ADMIN, SUPPORT), not insertion order.",
                        example = "[\"GUIDE\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<UserRole> roles,
        @Schema(
                        description = "The role this onboarding command just acquired.",
                        example = "GUIDE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UserRole acquiredRole,
        @Schema(
                        description =
                                "The newly created role's profile view: a GuideProfileResponse when"
                                        + " acquiredRole is GUIDE, or a ParticipantProfileResponse"
                                        + " when it is PARTICIPANT.",
                        oneOf = {GuideProfileResponse.class, ParticipantProfileResponse.class},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Object profile) {}
