package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Guide application / profile view, returned as an immutable record whose field names are the JSON
 * keys. The profile-level fields are null when the user has not started guide onboarding yet.
 */
@Schema(
        name = "GuideProfileResponse",
        description =
                "Guide application / profile view; profile-level fields are null before guide"
                        + " onboarding.")
public record GuideProfileResponse(
        @Schema(
                        description = "The guide's user id (UUID).",
                        example = "11111111-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String userId,
        @Schema(
                        description = "First name.",
                        example = "Maya",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String firstName,
        @Schema(
                        description = "Last name.",
                        example = "Chen",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String lastName,
        @Schema(
                        description = "Public display name.",
                        example = "Maya Chen",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String displayName,
        @Schema(
                        description = "Account email.",
                        example = "maya.chen@ncu.edu",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String email,
        @Schema(
                        description = "Account lifecycle status.",
                        example = "ACTIVE",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String accountStatus,
        @Schema(
                        description = "Id of the university the guide is affiliated with.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String universityId,
        @Schema(
                        description = "University name.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String universityName,
        @Schema(
                        description = "University short name / abbreviation.",
                        example = "NCU",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String universityShortName,
        @Schema(
                        description = "Field of study.",
                        example = "Marine Biology",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String major,
        @Schema(
                        description = "Class year (free-form label).",
                        example = "Junior",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String classYear,
        @Schema(
                        description = "Guide biography.",
                        example = "Third-year student and campus tour lead.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String bio,
        @Schema(
                        description = "BCP-47 language tags the guide speaks.",
                        example = "[\"en-US\",\"zh-CN\"]",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<String> languages,
        @Schema(
                        description = "Tour topics the guide specializes in.",
                        example = "[\"DORM_HOUSING\"]",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<String> specialties,
        @Schema(
                        description = "Default per-tour price in cents.",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long basePriceCents,
        @Schema(
                        description = "ISO-4217 currency for basePriceCents.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String currency,
        @Schema(
                        description = "Guide application review status.",
                        example = "APPROVED",
                        allowableValues = {
                            "DRAFT",
                            "PENDING_REVIEW",
                            "APPROVED",
                            "REJECTED",
                            "SUSPENDED"
                        },
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String applicationStatus,
        @Schema(
                        description = "University-email verification status.",
                        example = "VERIFIED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String verificationStatus,
        @Schema(
                        description =
                                "Degree level (free-form; e.g. the College Scorecard credential"
                                        + " title from GET /v1/meta/degrees).",
                        example = "Bachelor's Degree",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String degree,
        @Schema(
                        description =
                                "Average rating across all published reviews. Null when the guide"
                                        + " has no reviews yet.",
                        example = "4.5",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                BigDecimal avgRating,
        @Schema(
                        description = "Total published review count.",
                        example = "12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int reviewCount) {}
