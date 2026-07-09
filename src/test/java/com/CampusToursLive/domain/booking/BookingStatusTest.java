package com.CampusToursLive.domain.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** displayStatus() maps every internal BookingStatus to a stable frontend string. */
class BookingStatusTest {

    @Test
    void displayStatus_isNeverBlank_forEveryValue() {
        for (BookingStatus s : BookingStatus.values()) {
            assertFalse(s.displayStatus().isBlank(), s + " produced a blank displayStatus");
        }
    }

    @Test
    void displayStatus_mapsEachGroup() {
        assertEquals("DRAFT", BookingStatus.DRAFT.displayStatus());
        assertEquals("PENDING_PAYMENT", BookingStatus.PENDING_PAYMENT_AUTH.displayStatus());
        assertEquals("PENDING_PAYMENT", BookingStatus.PAYMENT_ACTION_REQUIRED.displayStatus());
        assertEquals("WAITING_FOR_GUIDE", BookingStatus.PENDING_GUIDE_ACCEPTANCE.displayStatus());
        assertEquals("CONFIRMED", BookingStatus.CONFIRMED.displayStatus());
        assertEquals("CONFIRMED", BookingStatus.IN_PROGRESS.displayStatus());
        assertEquals("COMPLETED", BookingStatus.COMPLETED.displayStatus());
        // Everything else collapses to the catch-all "CANCELLED" (the default arm).
        assertEquals("CANCELLED", BookingStatus.DECLINED_BY_GUIDE.displayStatus());
        assertEquals("CANCELLED", BookingStatus.EXPIRED.displayStatus());
        assertEquals("CANCELLED", BookingStatus.CANCELLED_BY_PARTICIPANT.displayStatus());
        assertEquals("CANCELLED", BookingStatus.REFUNDED.displayStatus());
    }
}
