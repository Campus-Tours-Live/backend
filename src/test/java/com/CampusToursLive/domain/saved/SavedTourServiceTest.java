package com.CampusToursLive.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SavedTourServiceTest {

    @Mock SavedTourRepository savedTours;
    @Mock TourOfferingRepository offerings;
    @Mock TourDiscoveryService discovery;

    private final UUID userId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    private SavedTourService service() {
        return new SavedTourService(savedTours, offerings, discovery);
    }

    @Test
    void save_insertsAndReportsNew_whenOfferingVisible() {
        when(offerings.findDiscoverableById(offeringId))
                .thenReturn(Optional.of(new TourOfferingEntity()));
        when(savedTours.insertIfAbsent(any(UUID.class), eq(userId), eq(offeringId))).thenReturn(1);

        assertThat(service().save(userId, offeringId)).isTrue();
    }

    @Test
    void save_isIdempotent_whenAlreadySaved() {
        when(offerings.findDiscoverableById(offeringId))
                .thenReturn(Optional.of(new TourOfferingEntity()));
        when(savedTours.insertIfAbsent(any(UUID.class), eq(userId), eq(offeringId))).thenReturn(0);

        assertThat(service().save(userId, offeringId)).isFalse();
    }

    @Test
    void save_throws404_andWritesNothing_whenOfferingNotVisible() {
        when(offerings.findDiscoverableById(offeringId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().save(userId, offeringId))
                .isInstanceOf(NotFoundException.class);
        verify(savedTours, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void unsave_deletesOnlyTheCallersRow() {
        service().unsave(userId, offeringId);

        verify(savedTours).deleteByUserIdAndTourOfferingId(userId, offeringId);
    }

    @Test
    void unsave_doesNotRequireTheOfferingToBeVisible() {
        when(savedTours.deleteByUserIdAndTourOfferingId(userId, offeringId)).thenReturn(0);

        service().unsave(userId, offeringId);

        verify(offerings, never()).findDiscoverableById(any());
    }

    @Test
    void list_mapsVisibleSavesToCards_keepingRepositoryTotal() {
        TourOfferingEntity row = new TourOfferingEntity();
        TourSummaryResponse card = mock(TourSummaryResponse.class);
        when(savedTours.findVisibleSavedOfferings(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 5), 6));
        when(discovery.toSummaries(List.of(row))).thenReturn(List.of(card));

        Page<TourSummaryResponse> res = service().list(userId, 1, 5);

        assertThat(res.getContent()).containsExactly(card);
        assertThat(res.getTotalElements()).isEqualTo(6);
        assertThat(res.getNumber()).isEqualTo(1);
        assertThat(res.getSize()).isEqualTo(5);
    }

    @Test
    void list_clampsLimitToFifty_andNegativePageToZero() {
        assertThat(requestedPage(-3, 500)).isEqualTo(PageRequest.of(0, 50));
    }

    @Test
    void list_clampsLimitToOne_whenBelowMinimum() {
        assertThat(requestedPage(0, 0)).isEqualTo(PageRequest.of(0, 1));
    }

    @Test
    void savedTourIds_returnsRepositoryIds() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(savedTours.findTourOfferingIdsByUserId(userId)).thenReturn(ids);

        assertThat(service().savedTourIds(userId)).isEqualTo(ids);
    }

    private Pageable requestedPage(int page, int limit) {
        when(savedTours.findVisibleSavedOfferings(eq(userId), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(discovery.toSummaries(List.of())).thenReturn(List.of());

        service().list(userId, page, limit);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savedTours).findVisibleSavedOfferings(eq(userId), captor.capture());
        return captor.getValue();
    }
}
