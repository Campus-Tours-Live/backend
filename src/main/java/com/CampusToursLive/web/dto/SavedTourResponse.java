package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SavedTourResponse", description = "Result of saving a tour.")
public record SavedTourResponse(
        @Schema(
                        description = "Saved tour offering id (UUID).",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String tourOfferingId,
        @Schema(
                        description = "False if the tour was already saved.",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean newlySaved) {}
