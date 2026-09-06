package com.CampusToursLive.domain.university;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import com.CampusToursLive.web.dto.UniversityDetailResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform university profile for {@code GET /universities/{slug}} — not the Scorecard directory.
 */
@Service
public class UniversityReadService {

    private final UniversityRepository universities;
    private final TourDiscoveryService tours;

    public UniversityReadService(UniversityRepository universities, TourDiscoveryService tours) {
        this.universities = universities;
        this.tours = tours;
    }

    /**
     * @throws ValidationException when the slug is blank
     * @throws NotFoundException when no platform university carries that slug
     */
    @Transactional(readOnly = true)
    public UniversityDetailResponse getBySlug(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.strip();
        if (slug.isEmpty()) {
            throw new ValidationException("slug is required");
        }

        UniversityEntity university =
                universities
                        .findBySlug(slug)
                        .orElseThrow(() -> new NotFoundException("University not found"));

        return toDetail(university, cheapestBookableTour(university));
    }

    /**
     * Reuses {@link TourDiscoveryService#list} so tourCount / from-price stay in sync with {@code
     * GET /tours?universityId=} (same bookability predicate). limit=1 + PRICE_ASC yields the
     * cheapest row's currency.
     */
    private Page<TourSummaryResponse> cheapestBookableTour(UniversityEntity university) {
        return tours.list(
                university.getId().toString(), null, null, TourDiscoverySort.PRICE_ASC, 0, 1);
    }

    private static UniversityDetailResponse toDetail(
            UniversityEntity university, Page<TourSummaryResponse> cheapest) {
        List<TourSummaryResponse> rows = cheapest.getContent();
        TourSummaryResponse from = rows.isEmpty() ? null : rows.get(0);

        return new UniversityDetailResponse(
                university.getId().toString(),
                university.getSlug(),
                university.getName(),
                university.getShortName(),
                university.getCity(),
                university.getRegion(),
                university.getTimezone(),
                university.getImageUrl(),
                university.getStatus().name(),
                cheapest.getTotalElements(),
                from != null ? from.priceCents() : null,
                from != null ? from.currency() : null);
    }
}
