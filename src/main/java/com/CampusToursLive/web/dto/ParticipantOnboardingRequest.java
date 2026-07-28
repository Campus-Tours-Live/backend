package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * POST body for the participant onboarding COMMAND (full create, Task 7's controller — not wired
 * yet). Distinct from {@link ParticipantProfileUpdateRequest} (the partial PATCH DTO). Participant
 * onboarding is lighter than guide onboarding: {@code ParticipantService.updateProfile} defaults
 * {@code participantType} to PROSPECTIVE and requires nothing else, so the only field enforced here
 * is a valid {@code participantType} — the minimal sensible first-time set for a command that
 * acquires the PARTICIPANT role. A missing/invalid value fails with 422 {@code VALIDATION_FAILED}.
 */
@Schema(
        name = "ParticipantOnboardingRequest",
        description =
                "Participant onboarding command: creates the participant role + profile in one"
                        + " call. Unlike ParticipantProfileUpdateRequest (partial PATCH),"
                        + " participantType is required — the minimal first-time-required field.")
public record ParticipantOnboardingRequest(
        @Schema(
                        description = "First name.",
                        example = "Sam",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String firstName,
        @Schema(
                        description = "Last name.",
                        example = "Rivera",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String lastName,
        @Schema(
                        description = "Public display name.",
                        example = "Sam Rivera",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String displayName,
        @NotBlank(message = "participantType is required")
                @Pattern(
                        regexp = "HIGH_SCHOOL|PROSPECTIVE|TRANSFER|INTERNATIONAL|PARENT|OTHER",
                        message =
                                "participantType must be one of HIGH_SCHOOL, PROSPECTIVE,"
                                        + " TRANSFER, INTERNATIONAL, PARENT, OTHER")
                @Schema(
                        description = "Participant type (controlled vocabulary).",
                        example = "PROSPECTIVE",
                        allowableValues = {
                            "HIGH_SCHOOL",
                            "PROSPECTIVE",
                            "TRANSFER",
                            "INTERNATIONAL",
                            "PARENT",
                            "OTHER"
                        },
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String participantType,
        @Schema(
                        description =
                                "Grade / education level. Free-text (no controlled vocabulary);"
                                        + " typical values include \"High school senior\","
                                        + " \"College freshman\", or \"Graduate\".",
                        example = "High school senior",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String gradeLevel,
        @Schema(
                        description = "Intended field of study.",
                        example = "Computer Science",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String intendedMajor,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "College Scorecard school ids the participant is"
                                                        + " interested in, as returned by GET"
                                                        + " /v1/meta/universities.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema =
                                @Schema(
                                        description = "College Scorecard school id (string).",
                                        example = "166683"))
                List<String> universitiesOfInterest,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Tour topic codes the participant is interested"
                                                        + " in (controlled vocabulary).",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema =
                                @Schema(
                                        description = "Tour topic code.",
                                        example = "DORM_HOUSING",
                                        allowableValues = {
                                            "GENERAL_CAMPUS",
                                            "DORM_HOUSING",
                                            "DINING_STUDENT_LIFE",
                                            "MAJOR_SPECIFIC",
                                            "INTERNATIONAL_STUDENT",
                                            "PARENT_FOCUSED",
                                            "FRESHMAN",
                                            "TRANSFER"
                                        }))
                List<String> topicsOfInterest,
        @Schema(
                        description = "Preferred BCP-47 language tag.",
                        example = "en-US",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String preferredLanguage,
        @Schema(
                        description = "IANA timezone.",
                        example = "America/New_York",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String timezone,
        @Schema(
                        description = "Free-form accessibility preferences (stored as JSON).",
                        example = "wheelchair-access",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String accessibilityPreferences) {}
