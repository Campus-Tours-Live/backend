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
 * Reads one platform university for its public profile page.
 *
 * <p>Distinct from {@link com.CampusToursLive.integration.scorecard.UniversityDirectory}, which
 * browses the national College Scorecard population. This serves the {@code universities} table —
 * the schools actually onboarded onto the platform — so a directory slug with no row here is a 404,
 * not an empty profile.
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
     * One university's profile plus its live-tour count and lowest price.
     *
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
     * The university's cheapest bookable tour, or an empty page when it has none.
     *
     * <p>Deliberately goes through {@link TourDiscoveryService#list} rather than a second count
     * query of its own. The marketplace's bookability rules live in one JPQL predicate ({@code
     * TourOfferingRepository.DISCOVERABLE_FROM_WHERE}), and a hand-rolled copy here is exactly how
     * a profile page ends up advertising "4 live tours" above a listing that shows three. Asking
     * the same service the listing asks makes the two figures the same figure: the page total is
     * the listing's {@code totalElements}, and the "from" price is the first row of {@code GET
     * /tours?universityId=…&sort=PRICE_ASC}.
     *
     * <p>Fetching one row rather than a bare count is what carries the currency: offerings hold
     * their own, so the lowest price has to be reported in the currency of the offering that
     * actually charges it, not in a platform-wide assumption.
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
                university.getStatus() != null ? university.getStatus().name() : null,
                cheapest.getTotalElements(),
                from != null ? from.priceCents() : null,
                from != null ? from.currency() : null);
    }
}
