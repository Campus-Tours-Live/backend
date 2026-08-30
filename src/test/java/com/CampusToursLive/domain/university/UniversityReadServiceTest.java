package com.CampusToursLive.domain.university;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import com.CampusToursLive.web.dto.UniversityDetailResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * UniversityReadService — the platform-university profile behind {@code GET /universities/{slug}}.
 *
 * <p>The interesting assertions here are not the field copies but the two figures the page leads
 * with: that they come from the marketplace's own listing rather than a private count, and that a
 * university with nothing bookable reports that honestly instead of a zero-priced tour.
 */
@ExtendWith(MockitoExtension.class)
class UniversityReadServiceTest {

    @Mock UniversityRepository universities;
    @Mock TourDiscoveryService tours;

    private static final UUID UNIVERSITY_ID =
            UUID.fromString("01a2c3d4-0000-4000-8000-000000000003");

    private UniversityReadService service() {
        return new UniversityReadService(universities, tours);
    }

    private static UniversityEntity university() {
        UniversityEntity u = new UniversityEntity();
        u.setId(UNIVERSITY_ID);
        u.setSlug("north-coast");
        u.setName("North Coast University");
        u.setShortName("NCU");
        u.setCity("Arcata");
        u.setRegion("CA");
        u.setTimezone("America/Los_Angeles");
        u.setImageUrl("https://images.example/ncu.png");
        u.setStatus(UniversityStatus.ACTIVE);
        return u;
    }

    /** A marketplace card; only the price and currency matter to this service. */
    private static TourSummaryResponse tourAt(long priceCents, String currency) {
        return new TourSummaryResponse(
                "o1a2c3d4-0000-4000-8000-000000000002",
                "North Campus highlights",
                "north-campus-highlights",
                "GENERAL_CAMPUS",
                UNIVERSITY_ID.toString(),
                "North Coast University",
                null,
                "11111111-0000-4000-8000-000000000001",
                "Maya Chen",
                "Marine Biology",
                "BS",
                2023,
                60,
                priceCents,
                currency,
                4.5,
                12,
                List.of("en-US"),
                List.of("Q_AND_A"),
                false);
    }

    /** One row out of {@code total} — what a {@code limit=1} marketplace page looks like. */
    private static Page<TourSummaryResponse> pageOf(TourSummaryResponse row, long total) {
        return new PageImpl<>(row == null ? List.of() : List.of(row), PageRequest.of(0, 1), total);
    }

    // --- the happy path ------------------------------------------------------------------------

    @Test
    void getBySlug_returnsTheProfileWithItsLiveTourCountAndLowestPrice() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(3800, "USD"), 4));

        UniversityDetailResponse detail = service().getBySlug("north-coast");

        assertThat(detail.id()).isEqualTo(UNIVERSITY_ID.toString());
        assertThat(detail.slug()).isEqualTo("north-coast");
        assertThat(detail.name()).isEqualTo("North Coast University");
        assertThat(detail.shortName()).isEqualTo("NCU");
        assertThat(detail.city()).isEqualTo("Arcata");
        assertThat(detail.region()).isEqualTo("CA");
        assertThat(detail.timezone()).isEqualTo("America/Los_Angeles");
        assertThat(detail.imageUrl()).isEqualTo("https://images.example/ncu.png");
        assertThat(detail.status()).isEqualTo("ACTIVE");
        assertThat(detail.tourCount()).isEqualTo(4);
        assertThat(detail.fromPriceCents()).isEqualTo(3800L);
        assertThat(detail.currency()).isEqualTo("USD");
    }

    /**
     * The whole reason this service borrows the marketplace listing instead of counting for itself:
     * the page's figure has to be the listing's figure. Pinning the exact call pins that — the same
     * university filter, no topic or text narrowing, cheapest first, one row.
     */
    @Test
    void getBySlug_readsItsFiguresOffTheMarketplaceListingItself() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(3800, "USD"), 4));

        service().getBySlug("north-coast");

        verify(tours).list(UNIVERSITY_ID.toString(), null, null, TourDiscoverySort.PRICE_ASC, 0, 1);
    }

    /**
     * The count is the listing's total, not the number of rows fetched. Asking for one row to learn
     * the cheapest price must not collapse "12 live tours" into "1".
     */
    @Test
    void getBySlug_countsEveryBookableTour_notJustTheOneRowItFetched() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(2500, "USD"), 12));

        assertThat(service().getBySlug("north-coast").tourCount()).isEqualTo(12);
    }

    /**
     * Offerings carry their own currency, so the "from" price is reported in the currency of the
     * offering that actually charges it rather than an assumed platform default.
     */
    @Test
    void getBySlug_reportsTheCheapestOfferingsOwnCurrency() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(2900, "CAD"), 2));

        assertThat(service().getBySlug("north-coast").currency()).isEqualTo("CAD");
    }

    // --- nothing bookable ----------------------------------------------------------------------

    /**
     * A university with no bookable tour has no lowest price, and saying so is the point: a 0 here
     * renders as "From $0.00" on the page — a free tour that does not exist.
     */
    @Test
    void getBySlug_universityWithNoBookableTours_hasNoPriceRatherThanAZeroOne() {
        when(universities.findBySlug("redwood-state")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("redwood-state");

        assertThat(detail.tourCount()).isZero();
        assertThat(detail.fromPriceCents()).isNull();
        assertThat(detail.currency()).isNull();
    }

    /**
     * A paused or archived university is still served — with its status — rather than hidden behind
     * a 404. Its tour count is 0 because the marketplace predicate excludes it, which is the honest
     * answer: those offerings exist but nobody can book them.
     */
    @Test
    void getBySlug_servesANonActiveUniversityWithItsRealStatus() {
        UniversityEntity paused = university();
        paused.setStatus(UniversityStatus.PAUSED);
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(paused));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("north-coast");

        assertThat(detail.status()).isEqualTo("PAUSED");
        assertThat(detail.tourCount()).isZero();
    }

    /** Optional columns stay null rather than becoming empty strings the page would render. */
    @Test
    void getBySlug_leavesUnsetOptionalColumnsNull() {
        UniversityEntity sparse = university();
        sparse.setShortName(null);
        sparse.setRegion(null);
        sparse.setImageUrl(null);
        sparse.setStatus(null);
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(sparse));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("north-coast");

        assertThat(detail.shortName()).isNull();
        assertThat(detail.region()).isNull();
        assertThat(detail.imageUrl()).isNull();
        assertThat(detail.status()).isNull();
    }

    // --- the misses ----------------------------------------------------------------------------

    /**
     * A slug with no platform row is a 404, and it is the common case rather than the exotic one:
     * the browse-by-state directory lists every U.S. school, while this table holds only the ones
     * onboarded here.
     */
    @Test
    void getBySlug_unknownSlugIsNotFound() {
        when(universities.findBySlug("not-on-the-platform")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getBySlug("not-on-the-platform"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("University not found");

        verifyNoInteractions(tours);
    }

    @Test
    void getBySlug_blankSlugIsRejectedBeforeAnyLookup() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service().getBySlug(blank))
                    .as("slug=%s", blank)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("slug is required");
        }

        verifyNoInteractions(universities, tours);
    }

    @Test
    void getBySlug_trimsSurroundingWhitespaceBeforeLookingUp() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        assertThat(service().getBySlug("  north-coast  ").slug()).isEqualTo("north-coast");

        verify(universities).findBySlug("north-coast");
    }
}
