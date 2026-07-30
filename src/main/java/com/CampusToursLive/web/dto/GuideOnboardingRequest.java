package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * POST body for the guide onboarding COMMAND (full create, Task 7's controller — not wired yet).
 * Distinct from {@link GuideProfileUpdateRequest} (the partial PATCH DTO): this command implies
 * submission in one call, so every field the service enforces on {@code submit=true} — university,
 * major, degree, verification email, bio, and at least one tour topic (see {@code
 * GuideService.updateProfile}) — is required here via bean validation instead of a {@code submit}
 * flag. A missing required field fails with 422 {@code VALIDATION_FAILED} (see {@code
 * GlobalExceptionHandler}), not a 500 partway through onboarding.
 */
@Schema(
        name = "GuideOnboardingRequest",
        description =
                "Guide onboarding command: creates the guide role + profile in one call. Unlike"
                        + " GuideProfileUpdateRequest (partial PATCH), the first-time-required"
                        + " fields are enforced by validation and there is no submit flag — the"
                        + " command itself implies submission.")
public record GuideOnboardingRequest(
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
        @NotBlank(message = "universityId is required")
                @Schema(
                        description = "Id of the university the guide is affiliated with.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityId,
        @NotBlank(message = "major is required")
                @Schema(
                        description = "Field of study.",
                        example = "Marine Biology",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String major,
        @Pattern(regexp = "\\d{4}", message = "classYear must be a 4-digit year")
                @Schema(
                        description = "Class year, as a 4-digit year (e.g. graduating class).",
                        // Quoted so swagger-core's example resolver parses this as a JSON string,
                        // not a JSON number -- an unquoted numeric-looking example (e.g. "2027")
                        // gets silently coerced to a number by io.swagger.v3.core.util
                        // .AnnotationsUtils#getExampleObject, which JSON-parses the example
                        // string outright and fails the "type": "string" Spectral check
                        // (oas3-valid-schema-example) once this schema is actually reachable from
                        // an operation.
                        example = "\"2027\"",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String classYear,
        @NotBlank(message = "bio is required")
                @Schema(
                        description = "Guide biography.",
                        example = "Third-year student and campus tour lead.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String bio,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Languages the guide speaks.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema = @Schema(description = "BCP-47 language tag.", example = "en-US"))
                List<String> spokenLanguages,
        @NotEmpty(message = "At least one tour topic is required")
                @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Tour topics the guide focuses on.",
                                        requiredMode = Schema.RequiredMode.REQUIRED),
                        schema =
                                @Schema(
                                        description = "Free-text tour topic label.",
                                        example = "Dorm & housing tours"))
                List<String> tourTopics,
        @NotBlank(message = "verificationEmail is required")
                @Email(message = "verificationEmail must be a valid email address")
                @Schema(
                        description =
                                "University email used for verification (method"
                                        + " UNIVERSITY_EMAIL).",
                        example = "maya.chen@ncu.edu",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String verificationEmail,
        @NotBlank(message = "degree is required")
                @Schema(
                        description =
                                "Degree level the guide is pursuing / holds, as returned by GET"
                                        + " /v1/meta/degrees for the selected university (the College"
                                        + " Scorecard credential title).",
                        example = "Bachelor's Degree",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String degree,
        @NotNull(message = "entryYear is required")
                @Schema(
                        description = "Year the guide entered this university, e.g. 2023.",
                        example = "2023",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer entryYear) {}
