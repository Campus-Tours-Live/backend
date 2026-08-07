package com.CampusToursLive.domain.booking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CartExpiryJob — a thin scheduled driver: it delegates to {@link
 * BookingService#expireStaleCartItems()} and must never let a failure escape the scheduler thread.
 */
@ExtendWith(MockitoExtension.class)
class CartExpiryJobTest {

    @Mock BookingService bookingService;

    private CartExpiryJob job() {
        return new CartExpiryJob(bookingService);
    }

    @Test
    void sweep_delegatesToService() {
        when(bookingService.expireStaleCartItems()).thenReturn(3);

        job().sweep();

        verify(bookingService).expireStaleCartItems();
    }

    @Test
    void sweep_swallowsServiceException() {
        when(bookingService.expireStaleCartItems()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> job().sweep());
        verify(bookingService).expireStaleCartItems();
    }
}
