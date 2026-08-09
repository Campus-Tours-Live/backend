package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PATCH /guide/profile body — guide application / onboarding.
 *
 * <p>Maps to: users (firstName, lastName, displayName) + guide_profiles (bio, spokenLanguages,
 * tourTopics) + guide_universities (universityId, major, classYear, degree, entryYear, schoolEmail
 * from verificationEmail, method UNIVERSITY_EMAIL implied).
 *
 * <p>When {@code submit} is true the application is finalized: required fields (university, major,
 * verification email) are enforced, a verification row is created, the GUIDE role is granted
 * (user_roles) and the guide's own guide_status moves to PENDING. The account-level status is
 * unchanged — role lifecycle lives on the profile, not the account.
 */
@Schema(
        name = "GuideProfileUpdateRequest",
        description =
                "Partial guide onboarding/application update. All fields optional; set submit=true"
                        + " to finalize the application.")
public record GuideProfileUpdateRequest(
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
                        description = "Id of the university the guide is affiliated with.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String universityId,
        @Schema(
                        description = "Field of study.",
                        example = "Marine Biology",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String major,
        @Schema(
                        description =
                                "Class year, as a 4-digit year (e.g. graduating class). Must fall"
                                        + " inside the window derived from entryYear and degree —"
                                        + " see GET /v1/meta/enrollment-years for that window.",
                        // Quoted so swagger-core parses this as a JSON string rather than coercing
                        // a numeric-looking example to a number and failing Spectral's
                        // oas3-valid-schema-example check — see GuideOnboardingRequest.classYear.
                        example = "\"2027\"",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String classYear,
        @Schema(
                        description = "Guide biography.",
                        example = "Third-year student and campus tour lead.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String bio,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Languages the guide speaks.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema = @Schema(description = "BCP-47 language tag.", example = "en-US"))
                List<String> spokenLanguages,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Tour topics the guide focuses on.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema =
                                @Schema(
                                        description = "Free-text tour topic label.",
                                        example = "Dorm & housing tours"))
                List<String> tourTopics,
        @Schema(
                        description =
                                "University email used for verification (method UNIVERSITY_EMAIL);"
                                        + " required when submit=true.",
                        example = "maya.chen@ncu.edu",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String verificationEmail,
        @Schema(
                        description =
                                "When true, finalize the application (enforce required fields,"
                                        + " grant the GUIDE role, move status to PENDING).",
                        example = "true",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Boolean submit,
        @Schema(
                        description =
                                "Degree level the guide is pursuing / holds, as returned by GET"
                                        + " /v1/meta/degrees for the selected university (the College"
                                        + " Scorecard credential title).",
                        example = "Bachelor's Degree",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String degree,
        @Schema(
                        description =
                                "Year the guide entered this university, e.g. 2023. Optional only"
                                        + " when the guide already has an affiliation row for this"
                                        + " universityId, whose stored value is then reused;"
                                        + " naming a university the guide is not yet affiliated"
                                        + " with requires it, and omitting it there is a 422.",
                        example = "2023",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer entryYear) {}
