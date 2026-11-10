package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingExpiryServiceTest {
    @Test
    void expiresClaimedRequestsAndWritesSystemHistory() {
        BookingRepository bookings = mock(BookingRepository.class);
        BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        booking.setGuideResponseDeadlineAt(now);
        when(bookings.claimExpiredGuideRequests(now)).thenReturn(List.of(booking));
        BookingExpiryService service =
                new BookingExpiryService(bookings, history, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(service.expireDueRequests()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getGuideResponseDeadlineAt()).isNull();
        ArgumentCaptor<BookingStatusHistoryEntity> captured =
                ArgumentCaptor.forClass(BookingStatusHistoryEntity.class);
        verify(history).save(captured.capture());
        assertThat(captured.getValue().getBookingId()).isEqualTo(booking.getId());
        assertThat(captured.getValue().getPreviousStatus())
                .isEqualTo(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        assertThat(captured.getValue().getNewStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(captured.getValue().getActorType()).isEqualTo(BookingActor.SYSTEM);
        assertThat(captured.getValue().getActorUserId()).isNull();
        assertThat(captured.getValue().getReasonCode()).isEqualTo("GUIDE_RESPONSE_EXPIRED");
        verify(bookings).flush();
    }

    @Test
    void emptyBatchDoesNotWriteHistory() {
        BookingRepository bookings = mock(BookingRepository.class);
        BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
        when(bookings.claimExpiredGuideRequests(any())).thenReturn(List.of());
        assertThat(
                        new BookingExpiryService(bookings, history, Clock.systemUTC())
                                .expireDueRequests())
                .isZero();
        verifyNoInteractions(history);
    }
}
