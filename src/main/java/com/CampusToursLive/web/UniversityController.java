package com.CampusToursLive.web;

import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.UniversityResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * University catalog (BFF maps /v1/universities → here). Backs the onboarding typeahead. `q`
 * filters by name/short name; `limit` caps results (max 50).
 */
@RestController
@RequestMapping("/universities")
public class UniversityController {

    private final UniversityRepository universities;

    public UniversityController(UniversityRepository universities) {
        this.universities = universities;
    }

    @GetMapping
    public ApiEnvelope<List<UniversityResponse>> list(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        List<UniversityResponse> items =
                universities.search(q.trim(), PageRequest.of(0, capped)).stream()
                        .map(UniversityResponse::from)
                        .toList();
        return ApiEnvelope.of(items);
    }
}
