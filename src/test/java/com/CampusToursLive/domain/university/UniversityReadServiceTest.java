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

/** Unit tests for {@link UniversityReadService}. */
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

    private static Page<TourSummaryResponse> pageOf(TourSummaryResponse row, long total) {
        return new PageImpl<>(row == null ? List.of() : List.of(row), PageRequest.of(0, 1), total);
    }

    @Test
    void getBySlug_returnsProfileWithTourCountAndLowestPrice() {
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

    @Test
    void getBySlug_readsStatsFromMarketplaceListing() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(3800, "USD"), 4));

        service().getBySlug("north-coast");

        verify(tours).list(UNIVERSITY_ID.toString(), null, null, TourDiscoverySort.PRICE_ASC, 0, 1);
    }

    @Test
    void getBySlug_tourCountIsListingTotal_notFetchedRowCount() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(2500, "USD"), 12));

        assertThat(service().getBySlug("north-coast").tourCount()).isEqualTo(12);
    }

    @Test
    void getBySlug_usesCheapestOfferingsCurrency() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(tourAt(2900, "CAD"), 2));

        assertThat(service().getBySlug("north-coast").currency()).isEqualTo("CAD");
    }

    @Test
    void getBySlug_noBookableTours_nullPriceAndCurrency() {
        when(universities.findBySlug("redwood-state")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("redwood-state");

        assertThat(detail.tourCount()).isZero();
        assertThat(detail.fromPriceCents()).isNull();
        assertThat(detail.currency()).isNull();
    }

    @Test
    void getBySlug_pausedUniversity_returnsStatusWithZeroTours() {
        UniversityEntity paused = university();
        paused.setStatus(UniversityStatus.PAUSED);
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(paused));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("north-coast");

        assertThat(detail.status()).isEqualTo("PAUSED");
        assertThat(detail.tourCount()).isZero();
    }

    @Test
    void getBySlug_optionalColumnsStayNullWhenUnset() {
        UniversityEntity sparse = university();
        sparse.setShortName(null);
        sparse.setRegion(null);
        sparse.setImageUrl(null);
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(sparse));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        UniversityDetailResponse detail = service().getBySlug("north-coast");

        assertThat(detail.shortName()).isNull();
        assertThat(detail.region()).isNull();
        assertThat(detail.imageUrl()).isNull();
        assertThat(detail.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getBySlug_unknownSlug_notFound() {
        when(universities.findBySlug("not-on-the-platform")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getBySlug("not-on-the-platform"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("University not found");

        verifyNoInteractions(tours);
    }

    @Test
    void getBySlug_blankSlug_rejectedBeforeLookup() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service().getBySlug(blank))
                    .as("slug=%s", blank)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("slug is required");
        }

        verifyNoInteractions(universities, tours);
    }

    @Test
    void getBySlug_trimsWhitespaceBeforeLookup() {
        when(universities.findBySlug("north-coast")).thenReturn(Optional.of(university()));
        when(tours.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(null, 0));

        assertThat(service().getBySlug("  north-coast  ").slug()).isEqualTo("north-coast");

        verify(universities).findBySlug("north-coast");
    }
}
