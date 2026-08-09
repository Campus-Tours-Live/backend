package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.user.RoleIneligibilityReason;
import io.swagger.v3.oas.annotations.media.Schema;

/** Body for GET /users/me/role-eligibility — can the caller acquire the given role. */
@Schema(
        name = "RoleEligibilityResponse",
        description =
                "Whether the caller may acquire the requested role. The authoritative source for"
                        + " this check — the bff routes signup/onboarding on it rather than"
                        + " inspecting profile fields itself.")
public record RoleEligibilityResponse(
        @Schema(
                        description = "Whether the caller may acquire the requested role.",
                        example = "false",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean eligible,
        @Schema(
                        description = "Why eligible is false; null when eligible is true.",
                        example = "PARENT_CANNOT_BECOME_GUIDE",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                RoleIneligibilityReason reason) {}
