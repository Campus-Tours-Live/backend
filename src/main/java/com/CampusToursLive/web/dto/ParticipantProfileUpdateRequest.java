package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PATCH /participant/profile body. All fields optional (partial update). Maps to: users
 * (displayName, preferredLanguage, timezone) + participant_profiles (participantType, gradeLevel,
 * intendedMajor) + participant_profiles.interests JSON (universitiesOfInterest, topicsOfInterest,
 * accessibilityPreferences).
 */
@Schema(
        name = "ParticipantProfileUpdateRequest",
        description = "Partial participant profile update. All fields optional.")
public record ParticipantProfileUpdateRequest(
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
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
