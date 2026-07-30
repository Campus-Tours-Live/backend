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
                "A guide's per-university affiliation (major/degree/class year/entry year +"
                        + " verification).")
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
                        description =
                                "Class year, as a 4-digit year (e.g. graduating class). Always"
                                        + " inside the window derived from entryYear and degree —"
                                        + " see GET /v1/meta/enrollment-years for that window.",
                        // Quoted so swagger-core parses this as a JSON string rather than coercing
                        // a numeric-looking example to a number and failing Spectral's
                        // oas3-valid-schema-example check — see GuideOnboardingRequest.classYear.
                        example = "\"2027\"",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String classYear,
        @Schema(
                        description = "Year the guide entered this university, e.g. 2023.",
                        example = "2023",
                        // NOT NULL on guide_universities since CTL-97 — every affiliation row this
                        // view is built from has one, so it is always present in the response.
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer entryYear,
        @Schema(
                        description = "University-email verification status for this university.",
                        example = "VERIFIED",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String verificationStatus) {}
