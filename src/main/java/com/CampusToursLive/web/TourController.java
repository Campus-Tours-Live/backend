package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.TourDetailResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.UUID;
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
public class TourController {

    private final TourDiscoveryService discovery;

    public TourController(TourDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping
    public ApiEnvelope<List<TourSummaryResponse>> list(
            @RequestParam(name = "universityId", required = false) String universityId,
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "sort", required = false, defaultValue = "RECOMMENDED")
                    String sort,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        TourDiscoverySort parsedSort = TourDiscoveryService.parseSort(sort);
        return ApiEnvelope.of(discovery.list(universityId, topic, q, parsedSort, limit));
    }

    @GetMapping("/{tourId}")
    public ApiEnvelope<TourDetailResponse> get(@PathVariable UUID tourId) {
        return ApiEnvelope.of(discovery.getById(tourId));
    }
}
