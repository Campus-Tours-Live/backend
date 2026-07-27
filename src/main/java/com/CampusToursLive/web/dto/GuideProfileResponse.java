package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Guide profile view — role-scoped ({@code GET /guide/profile}), immutable record whose field names
 * are the JSON keys. No identity fields (user id, name, email, account status): those live only on
 * {@code GET /userinfo}. The profile-level fields are null when the user has not started guide
 * onboarding yet. Per-university affiliation (university id/name, major, degree, class year,
 * verification status) lives in {@code universities[]}, one entry per {@code guide_universities}
 * row — a guide may be affiliated with more than one school.
 */
@Schema(
        name = "GuideProfileResponse",
        description =
                "Guide profile view; role-scoped (no identity fields — see /userinfo)."
                        + " Profile-level fields are null before guide onboarding.")
public record GuideProfileResponse(
        @Schema(
                        description = "Guide application verification status.",
                        example = "VERIFIED",
                        allowableValues = {"PENDING", "VERIFIED", "REJECTED"},
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String applicationStatus,
        @Schema(
                        description =
                                "Per-university affiliations (major/degree/class year/verification),"
                                        + " one entry per guide_universities row.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<GuideUniversityView> universities,
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
