package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Platform university profile ({@code universities} table) plus live-tour count and lowest price.
 * Not the national Scorecard directory — unknown slugs 404.
 */
@Schema(
        name = "UniversityDetail",
        description =
                "Platform university profile plus live-tour count and lowest price."
                        + " Platform rows only — not the national Scorecard directory.")
public record UniversityDetailResponse(
        @Schema(
                        description = "University id (UUID).",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "URL-safe slug — the key this endpoint is addressed by.",
                        example = "north-coast",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String slug,
        @Schema(
                        description = "Official university name.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(
                        description = "Short name / abbreviation. Null when unset.",
                        example = "NCU",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String shortName,
        @Schema(
                        description = "City the campus is in.",
                        example = "Arcata",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String city,
        @Schema(
                        description = "Region / state. Null when unset.",
                        example = "CA",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String region,
        @Schema(
                        description = "IANA timezone for campus tour times.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description = "Campus photo URL (Cloudflare R2). Null when unmapped.",
                        example =
                                "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/North%20Coast%20University.png",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String imageUrl,
        @Schema(
                        description =
                                "University lifecycle. PAUSED/ARCHIVED always report tourCount 0.",
                        example = "ACTIVE",
                        allowableValues = {"ACTIVE", "PAUSED", "ARCHIVED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description =
                                "Bookable tour count — same total as GET /tours?universityId=.",
                        example = "4",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long tourCount,
        @Schema(
                        description = "Lowest bookable price in cents. Null when tourCount is 0.",
                        example = "3800",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long fromPriceCents,
        @Schema(
                        description =
                                "ISO-4217 currency of fromPriceCents (cheapest offering)."
                                        + " Null when tourCount is 0.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String currency) {}
