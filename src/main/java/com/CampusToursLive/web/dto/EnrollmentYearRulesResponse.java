package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The enrolment-year rules the client needs to validate and to describe the two year fields.
 *
 * <p>Rule DATA, not a lookup table: degree strings are free-text College Scorecard credential
 * titles, so the set cannot be enumerated in advance. The client applies the same first-hit
 * substring match the server does — which is why the array order is part of the contract.
 */
@Schema(
        name = "EnrollmentYearRules",
        description =
                "Validation rules for the guide's enrolment year and expected graduation year.")
public record EnrollmentYearRulesResponse(
        @Schema(
                        description =
                                "Inclusive window of acceptable enrolment years, computed from the"
                                        + " server's UTC clock.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                YearRangeView entryYear,
        @Schema(
                        description =
                                "Ordered rules mapping a degree title to the longest time that"
                                        + " programme takes, counted FROM ENROLMENT. Apply them in"
                                        + " order and take the first whose keywords the"
                                        + " lower-cased, trimmed degree contains.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<DegreeRuleView> maxYearsToGraduate,
        @Schema(
                        description = "Used when a degree matches no rule.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int defaultMaxYearsToGraduate) {

    // NO field-level `example` values on the rule numbers below. They would be copies of the rule
    // table that NOTHING can pin: the response-level example is covered by
    // theDocumentedExampleMatchesAWireResponse, but a per-field `example = "6"` is invisible to it
    // and would keep advertising 6 after the rule became 5. A literal no test can protect is
    // strictly worse than no literal — the description carries the meaning, and the operation-level
    // example (ApiExamples.ENROLLMENT_YEARS) shows real values that stay honest.
    @Schema(name = "YearRange", description = "An inclusive [min, max] year window.")
    public record YearRangeView(
            @Schema(
                            description = "Earliest acceptable year.",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int min,
            @Schema(
                            description = "Latest acceptable year.",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int max) {}

    @Schema(name = "DegreeRule", description = "Keyword group → longest time to graduate.")
    public record DegreeRuleView(
            @Schema(
                            description =
                                    "Lower-case substrings; a degree matches if it contains ANY of"
                                            + " them.",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    List<String> matches,
            @Schema(
                            description = "Longest years to graduate, counted from enrolment.",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int years) {}
}
