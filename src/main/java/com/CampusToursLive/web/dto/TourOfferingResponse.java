package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A guide's tour offering, as returned by the offerings endpoints — an immutable record whose field
 * names are the JSON keys.
 */
@Schema(
        name = "TourOfferingResponse",
        description = "A guide's tour offering as returned by the offering endpoints.")
public record TourOfferingResponse(
        @Schema(
                        description = "Offering id (UUID).",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Public title.",
                        example = "North Campus highlights",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @Schema(
                        description = "URL-safe slug.",
                        example = "north-campus-highlights",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String slug,
        @Schema(
                        description = "Offering lifecycle status.",
                        example = "ACTIVE",
                        allowableValues = {"DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description = "Controlled tour topic.",
                        example = "GENERAL_CAMPUS",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String topic,
        @Schema(
                        description = "Id of the university this offering is at.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityId,
        @Schema(
                        description = "Tour duration in minutes.",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer durationMin,
        @Schema(
                        description = "Price in the smallest currency unit (cents).",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long priceCents,
        @Schema(
                        description = "ISO-4217 currency code.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currency,
        @Schema(
                        description = "Longer marketing description.",
                        example = "A guided walk through the north campus.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String description) {}
