package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PATCH /guide/profile body — guide application / onboarding.
 *
 * <p>Maps to: users (firstName, lastName, displayName) + guide_profiles (bio, languages,
 * specialties, basePriceCents) + guide_universities (universityId, major, classYear, degree,
 * schoolEmail from verificationEmail, method UNIVERSITY_EMAIL implied).
 *
 * <p>When {@code submit} is true the application is finalized: required fields (university, major,
 * verification email) are enforced, a verification row is created, the GUIDE role is granted
 * (user_roles) and the guide's own application_status moves to PENDING. The account-level status is
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
                                "Class year. Free-text (no controlled vocabulary); typical values"
                                        + " are Freshman/Sophomore/Junior/Senior/Graduate.",
                        example = "Junior",
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
                List<String> languages,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Tour specialties the guide focuses on.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema =
                                @Schema(
                                        description = "Free-text specialty label.",
                                        example = "Dorm & housing tours"))
                List<String> specialties,
        @Schema(
                        description =
                                "Default per-tour price in integer US cents (e.g. 2500 ="
                                        + " $25.00).",
                        example = "4200",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long basePriceCents,
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
                String degree) {}
