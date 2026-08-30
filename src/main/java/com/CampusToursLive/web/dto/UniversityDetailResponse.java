package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One platform university's public profile, plus the two figures its page leads with.
 *
 * <p><strong>Platform rows only.</strong> This is the {@code universities} table — the schools that
 * have actually been onboarded and can carry a tour — not the national College Scorecard directory
 * that {@code GET /universities?state=} browses. Most directory schools have no row here, which is
 * why an unknown slug is a 404 rather than an empty profile.
 *
 * <p>{@code tourCount} and {@code fromPriceCents} are read off the same marketplace query {@code
 * GET /tours?universityId=} runs, so the "4 live tours" on this page can never disagree with what
 * that listing actually returns. Both therefore obey the marketplace's bookability rules (an ACTIVE
 * offering, by a VERIFIED guide, at an ACTIVE university) — a paused or archived university reports
 * zero live tours even while its offering rows still exist.
 */
@Schema(
        name = "UniversityDetail",
        description =
                "One platform university's public profile plus its live-tour count and lowest"
                        + " price. Platform rows only — not the national Scorecard directory.")
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
                        description =
                                "Short name / abbreviation. Null when the directory has none.",
                        example = "NCU",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String shortName,
        @Schema(
                        description = "City the campus is in.",
                        example = "Arcata",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String city,
        @Schema(
                        description = "Region / state. Null for a row that predates the field.",
                        example = "CA",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String region,
        @Schema(
                        description = "IANA timezone the campus keeps its tour times in.",
                        example = "America/Los_Angeles",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timezone,
        @Schema(
                        description =
                                "Campus photo URL (Cloudflare R2). Null when no image is mapped for"
                                        + " this university.",
                        example =
                                "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/North%20Coast%20University.png",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String imageUrl,
        @Schema(
                        description =
                                "Marketplace lifecycle of the university. Only ACTIVE universities"
                                        + " carry bookable tours, so PAUSED and ARCHIVED always"
                                        + " report tourCount 0.",
                        example = "ACTIVE",
                        allowableValues = {"ACTIVE", "PAUSED", "ARCHIVED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description =
                                "How many bookable tours this university has right now. Always"
                                        + " equals the total GET /tours?universityId= reports —"
                                        + " both are read off the same query.",
                        example = "4",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long tourCount,
        @Schema(
                        description =
                                "Lowest bookable tour price, in the smallest currency unit (cents)."
                                        + " Null when tourCount is 0.",
                        example = "3800",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long fromPriceCents,
        @Schema(
                        description =
                                "ISO-4217 currency of fromPriceCents — the cheapest offering's own"
                                        + " currency, not a platform-wide assumption. Null when"
                                        + " tourCount is 0.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String currency) {}
