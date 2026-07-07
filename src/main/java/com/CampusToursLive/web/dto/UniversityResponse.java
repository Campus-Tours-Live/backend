package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.university.UniversityEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public catalog item for the universities picker. */
@Schema(
        name = "UniversityResponse",
        description = "Public catalog item for the universities picker / typeahead.")
public record UniversityResponse(
        @Schema(
                        description = "University id (UUID).",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "URL-safe slug.",
                        example = "north-coast",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String slug,
        @Schema(
                        description = "Full university name.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(
                        description = "Short name / abbreviation.",
                        example = "NCU",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String shortName,
        @Schema(
                        description = "City.",
                        example = "Arcata",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String city,
        @Schema(
                        description = "Region / state.",
                        example = "CA",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String region) {

    public static UniversityResponse from(UniversityEntity u) {
        return new UniversityResponse(
                u.getId().toString(),
                u.getSlug(),
                u.getName(),
                u.getShortName(),
                u.getCity(),
                u.getRegion());
    }
}
