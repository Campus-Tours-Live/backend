package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body for POST /admin/guides/{userId}/decision — APPROVED or REJECTED. */
@Schema(
        name = "GuideDecisionRequest",
        description = "Admin decision on a pending guide application.")
public record GuideDecisionRequest(
        @Schema(
                        description = "The review outcome to record on the guide's application.",
                        example = "APPROVED",
                        allowableValues = {"APPROVED", "REJECTED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String decision) {}
