package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Participant profile view — flat, role-scoped ({@code GET /participant/profile}), immutable record
 * whose field names are the JSON keys. No identity fields (user id, name, email, account status):
 * those live only on {@code GET /userinfo}. The interest fields ({@code topicsOfInterest}, {@code
 * universitiesOfInterest}, {@code accessibilityPreferences}) come from a free-form JSON blob, so
 * they stay typed as {@code Object}. Profile-level fields are null before participant onboarding.
 */
@Schema(
        name = "ParticipantProfileResponse",
        description =
                "Participant profile view; flat and role-scoped (no identity fields — see"
                        + " /userinfo). Profile-level fields are null before participant"
                        + " onboarding.")
public record ParticipantProfileResponse(
        @Schema(
                        description = "Participant application status.",
                        example = "VERIFIED",
                        allowableValues = {"PENDING", "VERIFIED"},
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String participantStatus,
        @Schema(
                        description = "Participant type.",
                        example = "PROSPECTIVE_STUDENT",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String type,
        @Schema(
                        description = "Grade / education level.",
                        example = "HIGH_SCHOOL_SENIOR",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String gradeLevel,
        @Schema(
                        description = "Intended field of study.",
                        example = "Computer Science",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String intendedMajor,
        @Schema(
                        description = "Whether a guardian is required (minors).",
                        example = "false",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Boolean guardianRequired,
        @Schema(
                        description =
                                "Free-form interests blob: tour topics of interest (array of topic"
                                        + " codes).",
                        example = "[\"DORM_HOUSING\"]",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Object topicsOfInterest,
        @Schema(
                        description =
                                "Free-form interests blob: universities of interest (array of"
                                        + " College Scorecard school ids).",
                        example = "[\"166683\"]",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Object universitiesOfInterest,
        @Schema(
                        description = "Free-form accessibility preferences blob.",
                        example = "null",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Object accessibilityPreferences,
        @Schema(
                        description = "Preferred BCP-47 language tag.",
                        example = "en-US",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String preferredLanguage,
        @Schema(
                        description = "IANA timezone.",
                        example = "America/New_York",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String timezone) {}
