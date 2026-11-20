package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.guide.GuideDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /admin/guides/{userId}/decision. */
@Schema(
        name = "GuideDecisionRequest",
        description = "Admin verdict on a PENDING guide application.")
public record GuideDecisionRequest(
        @NotNull(message = "decision is required")
                @Schema(
                        description = "APPROVE or REJECT.",
                        example = "APPROVE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                GuideDecision decision,
        @Size(max = 500, message = "reason must be at most 500 characters")
                @Schema(
                        description = "Required when rejecting. Optional when approving.",
                        example = "School email could not be verified",
                        maxLength = 500,
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reason) {}
