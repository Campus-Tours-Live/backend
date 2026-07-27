package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Guide profile view — flat, role-scoped ({@code GET /guide/profile}), immutable record whose field
 * names are the JSON keys. No identity fields (user id, name, email, account status): those live
 * only on {@code GET /userinfo}. The profile-level fields are null when the user has not started
 * guide onboarding yet. Still single-university for this phase; a {@code universities[]} array is a
 * later phase.
 */
@Schema(
        name = "GuideProfileResponse",
        description =
                "Guide profile view; flat and role-scoped (no identity fields — see /userinfo)."
                        + " Profile-level fields are null before guide onboarding.")
public record GuideProfileResponse(
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
                        description =
                                "Degree level (free-form; e.g. the College Scorecard credential"
                                        + " title from GET /v1/meta/degrees).",
                        example = "Bachelor's Degree",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String degree,
        @Schema(
                        description = "University-email verification status.",
                        example = "VERIFIED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String verificationStatus,
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
                String currency) {}
