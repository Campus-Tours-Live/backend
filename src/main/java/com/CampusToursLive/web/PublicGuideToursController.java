package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of a guide's tours (Core: /guides/{guideId}/tours). Anonymous, like the tour catalog
 * — pairs with the public guide profile so a visitor can see what a guide offers. Returns only
 * discoverable offerings (ACTIVE offering, VERIFIED guide, ACTIVE university); an unknown or
 * non-verified guide yields an empty page.
 */
@RestController
@RequestMapping("/guides")
@Tag(
        name = "Public guide tours",
        description =
                "A verified guide's discoverable tour offerings — anonymous, paginated, same shape"
                        + " as the public tour catalog.")
public class PublicGuideToursController {

    private final TourDiscoveryService discovery;

    public PublicGuideToursController(TourDiscoveryService discovery) {
        this.discovery = discovery;
    }

    /** A page of a guide's discoverable tours. */
    @Operation(
            summary = "List a guide's tours",
            description =
                    "Returns a page of the guide's discoverable tour offerings (active offerings by"
                            + " this verified guide at active universities), newest/recommended"
                            + " first. Anonymous. An unknown or non-verified guide yields an empty"
                            + " page.")
    @ApiResponse(
            responseCode = "200",
            description = "A page of the guide's tours.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_SUMMARY_LIST)))
    @GetMapping("/{guideId}/tours")
    public ApiEnvelope<PagedResponse<TourSummaryResponse>> tours(
            @PathVariable UUID guideId,
            @Parameter(description = "Zero-based page index.")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page,
            @Parameter(description = "Page size — max rows per page.")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit) {
        return ApiEnvelope.of(PagedResponse.of(discovery.listByGuide(guideId, page, limit)));
    }
}
