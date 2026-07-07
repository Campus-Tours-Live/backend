package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public marketplace card for a bookable tour offering. */
@Schema(
        name = "TourSummaryResponse",
        description = "Public marketplace card for a bookable tour offering.")
public record TourSummaryResponse(
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
                        description = "University name.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityName,
        @Schema(
                        description = "Id of the guide who offers the tour.",
                        example = "11111111-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideId,
        @Schema(
                        description = "Guide's public display name.",
                        example = "Maya Chen",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideDisplayName,
        @Schema(
                        description = "Tour duration in minutes.",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int durationMin,
        @Schema(
                        description = "Price in the smallest currency unit (cents).",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long priceCents,
        @Schema(
                        description = "ISO-4217 currency code.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currency,
        @Schema(
                        description = "Average review rating (0–5).",
                        example = "4.5",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                double avgRating,
        @Schema(
                        description = "Number of reviews backing avgRating.",
                        example = "12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int reviewCount) {}
