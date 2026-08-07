package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * How many universities each state has — the numbers on the browse-by-state page.
 *
 * <p>Every figure here is the size of the list {@code GET /universities?state=…} returns for that
 * state, because both are read off one directory snapshot. A count that disagreed with the page
 * behind it is not a bug that can happen.
 */
@Schema(
        name = "StateUniversityCounts",
        description = "University counts per state for the browse-by-state directory.")
public record StateUniversityCountsResponse(
        @Schema(
                        description =
                                "USPS state code → number of universities. Always all 50 states"
                                        + " plus DC. Territories are not listed.",
                        example = "{\"CA\":148,\"NY\":167,\"TX\":101}",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Map<String, Integer> byState,
        @Schema(
                        description =
                                "The sum of byState — every university across the states and DC."
                                        + " Excludes the territories, so this sits below the directory's"
                                        + " national figure.",
                        example = "1903",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int total) {}
