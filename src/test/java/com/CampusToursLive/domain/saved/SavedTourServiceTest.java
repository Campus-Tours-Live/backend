package com.CampusToursLive.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedTourServiceTest {

    @Mock SavedTourRepository savedTours;
    @Mock TourOfferingRepository offerings;

    private final UUID userId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    private SavedTourService service() {
        return new SavedTourService(savedTours, offerings);
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
}
