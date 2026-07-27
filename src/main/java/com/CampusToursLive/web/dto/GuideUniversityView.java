package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of a guide's {@code guide_universities} affiliation, as exposed inside {@code
 * GuideProfileResponse#universities()}. Deliberately excludes {@code schoolEmail} — that column is
 * PII (the guide's university-issued email address used for verification) and must never be
 * serialized to a client.
 */
@Schema(
        name = "GuideUniversityView",
        description =
                "A guide's per-university affiliation (major/degree/class year + verification).")
public record GuideUniversityView(
        @Schema(
                        description = "Id of this university.",
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
                        description = "Field of study at this university.",
                        example = "Marine Biology",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String major,
        @Schema(
                        description =
                                "Degree level (free-form; e.g. the College Scorecard credential"
                                        + " title from GET /v1/meta/degrees).",
                        example = "Bachelor's Degree",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String degree,
        @Schema(
                        description = "Class year (free-form label).",
                        example = "Junior",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String classYear,
        @Schema(
                        description = "University-email verification status for this university.",
                        example = "VERIFIED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String verificationStatus) {}
