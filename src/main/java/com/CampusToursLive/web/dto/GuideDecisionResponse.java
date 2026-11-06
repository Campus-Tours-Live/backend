package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of POST /admin/guides/{userId}/decision. */
@Schema(name = "GuideDecisionResponse", description = "The guide's status after an admin decision.")
public record GuideDecisionResponse(
        @Schema(
                        description = "The decided guide's user id.",
                        example = "a1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String userId,
        @Schema(
                        description = "Guide application status after the decision.",
                        example = "VERIFIED",
                        allowableValues = {"VERIFIED", "REJECTED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status) {}
