package com.CampusToursLive.web;

import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.UniversityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * University catalog (BFF maps /v1/universities → here). Backs the onboarding typeahead. `q`
 * filters by name/short name; `limit` caps results (max 50); and `page` selects a zero-based page.
 * The stable order makes the typeahead's incremental loading deterministic.
 */
@RestController
@RequestMapping("/universities")
@Tag(
        name = "Universities",
        description = "Public university catalog backing the onboarding typeahead.")
public class UniversityController {

    private final UniversityRepository universities;

    public UniversityController(UniversityRepository universities) {
        this.universities = universities;
    }

    @Operation(
            summary = "Search universities",
            description =
                    "Returns active universities matching the query, for the onboarding typeahead."
                            + " Public — no role required beyond a valid platform JWT.")
    @ApiResponse(
            responseCode = "200",
            description = "Matching universities for the requested page (capped by limit).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.UNIVERSITY_LIST)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid platform JWT.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping
    public ApiEnvelope<List<UniversityResponse>> list(
            @Parameter(
                            description =
                                    "Case-insensitive filter on name / short name; blank matches all.")
                    @RequestParam(name = "q", required = false, defaultValue = "")
                    String q,
            @Parameter(description = "Maximum rows to return; clamped to the range 1–50.")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit,
            @Parameter(description = "Zero-based result page; negative values are treated as 0.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page) {
        int capped = Math.min(Math.max(limit, 1), 50);
        int safePage = Math.max(page, 0);
        List<UniversityResponse> items =
                universities.search(q.trim(), PageRequest.of(safePage, capped)).stream()
                        .map(UniversityResponse::from)
                        .toList();
        return ApiEnvelope.of(items);
    }
}
