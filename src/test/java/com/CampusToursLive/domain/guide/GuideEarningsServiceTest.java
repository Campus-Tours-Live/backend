package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.web.dto.GuideEarningsResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideEarningsService — assembles the earnings DTO from two booking aggregations (this-month
 * earnings + upcoming payout). Covers the happy path, the currency constant, and correct
 * calendar-month boundaries.
 */
@ExtendWith(MockitoExtension.class)
class GuideEarningsServiceTest {

    @Mock BookingRepository bookings;

    private GuideEarningsService service() {
        return new GuideEarningsService(bookings);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    @Test
    void getEarnings_returnsAssembledDto() {
        UUID userId = UUID.randomUUID();

        when(bookings.sumGuideEarningsThisMonthByUserId(
                        eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(8400L);
        when(bookings.sumGuideUpcomingPayoutByUserId(userId)).thenReturn(4200L);

        GuideEarningsResponse result = service().getEarnings(user(userId));

        assertEquals(8400L, result.earningsThisMonthCents());
        assertEquals(4200L, result.upcomingPayoutCents());
        assertEquals("USD", result.currency());
    }

    @Test
    void getEarnings_returnsZeros_whenNoBookings() {
        UUID userId = UUID.randomUUID();

        when(bookings.sumGuideEarningsThisMonthByUserId(
                        eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(bookings.sumGuideUpcomingPayoutByUserId(userId)).thenReturn(0L);

        GuideEarningsResponse result = service().getEarnings(user(userId));

        assertEquals(0L, result.earningsThisMonthCents());
        assertEquals(0L, result.upcomingPayoutCents());
        assertEquals("USD", result.currency());
    }

    @Test
    void getEarnings_passesCorrectMonthBoundariesToRepository() {
        UUID userId = UUID.randomUUID();

        java.time.ZoneOffset utc = java.time.ZoneOffset.UTC;
        when(bookings.sumGuideEarningsThisMonthByUserId(
                        eq(userId), any(Instant.class), any(Instant.class)))
                .thenAnswer(
                        inv -> {
                            Instant from = inv.getArgument(1);
                            Instant to = inv.getArgument(2);
                            java.time.ZonedDateTime zFrom = from.atZone(utc);
                            java.time.ZonedDateTime zTo = to.atZone(utc);
                            assertEquals(1, zFrom.getDayOfMonth(), "from must be the 1st");
                            assertEquals(0, zFrom.getHour(), "from must be midnight UTC");
                            assertEquals(1, zTo.getDayOfMonth(), "to must be the 1st");
                            assertEquals(0, zTo.getHour(), "to must be midnight UTC");
                            assertEquals(
                                    zFrom.getMonth().plus(1).getValue() % 12 == 0
                                            ? 12
                                            : zFrom.getMonth().plus(1).getValue(),
                                    zTo.getMonthValue() == 1 ? 1 : zTo.getMonthValue());
                            return 0L;
                        });
        when(bookings.sumGuideUpcomingPayoutByUserId(userId)).thenReturn(0L);

        service().getEarnings(user(userId));
    }
}
