package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.TourDetailResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public marketplace catalog (BFF maps /v1/tours → here). Returns only ACTIVE offerings from
 * APPROVED guides at active universities — no auth role required beyond the platform JWT.
 */
@RestController
@RequestMapping("/tours")
@Tag(
        name = "Tours",
        description =
                "Public marketplace catalog. Returns only ACTIVE offerings from APPROVED guides at"
                        + " active universities; no role required beyond a valid platform JWT.")
public class TourController {

    private final TourDiscoveryService discovery;

    public TourController(TourDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @Operation(
            summary = "Search tours",
            description =
                    "Lists bookable tour offerings for the marketplace, filtered and sorted by the"
                            + " query parameters.")
    @ApiResponse(
            responseCode = "200",
            description = "Matching marketplace tours.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_SUMMARY_LIST)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid platform JWT.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "422",
            description = "Unknown sort value.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping
    public ApiEnvelope<PagedResponse<TourSummaryResponse>> list(
            @Parameter(description = "Filter to a single university id (UUID).")
                    @RequestParam(name = "universityId", required = false)
                    String universityId,
            @Parameter(
                            description =
                                    "Filter by one or more tour topic codes (repeatable, or comma"
                                            + " list). Empty/all = no filter. See GET"
                                            + " /meta/tour-topics.")
                    @RequestParam(name = "topic", required = false)
                    java.util.List<String> topic,
            @Parameter(description = "Free-text search over title / description.")
                    @RequestParam(name = "q", required = false, defaultValue = "")
                    String q,
            @Parameter(
                            description = "Sort order.",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "RECOMMENDED",
                                                "PRICE_ASC",
                                                "PRICE_DESC",
                                                "RATING"
                                            }))
                    @RequestParam(name = "sort", required = false, defaultValue = "RECOMMENDED")
                    String sort,
            @Parameter(description = "Zero-based page index.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page,
            @Parameter(description = "Page size — max rows per page.")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit) {
        TourDiscoverySort parsedSort = TourDiscoveryService.parseSort(sort);
        Page<TourSummaryResponse> result =
                discovery.list(universityId, topic, q, parsedSort, page, limit);
        return ApiEnvelope.of(PagedResponse.of(result));
    }

    @Operation(
            summary = "Get a tour",
            description = "Returns the full public detail for a single bookable tour offering.")
    @ApiResponse(
            responseCode = "200",
            description = "The tour detail.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_DETAIL)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid platform JWT.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description = "No bookable tour with that id.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "tourId is not a valid UUID.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping("/{tourId}")
    public ApiEnvelope<TourDetailResponse> get(
            @Parameter(description = "Id (UUID) of the tour offering.") @PathVariable UUID tourId) {
        return ApiEnvelope.of(discovery.getById(tourId));
    }
}
