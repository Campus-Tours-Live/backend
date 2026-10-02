package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of saving a tour — the tour is saved either way; {@code newlySaved} says whether now. */
@Schema(
        name = "SavedTourResponse",
        description = "Result of saving a tour for the current participant.")
public record SavedTourResponse(
        @Schema(
                        description = "Id of the saved tour offering (UUID).",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String tourOfferingId,
        @Schema(
                        description =
                                "True if this request created the save; false if the tour was"
                                        + " already saved (the request was a no-op).",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean newlySaved) {}
