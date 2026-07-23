package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.GuideDashboardStatsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideEarningsService — assembles the dashboard stats DTO from a guide profile (rating /
 * reviewCount) and two booking aggregations (this-month earnings + upcoming payout). Covers the
 * happy path, the NotFoundException when no profile exists, the currency-null fallback, and the
 * null avgRating case for guides who have no reviews yet.
 */
@ExtendWith(MockitoExtension.class)
class GuideEarningsServiceTest {

    @Mock GuideProfileRepository guides;
    @Mock BookingRepository bookings;

    private GuideEarningsService service() {
        return new GuideEarningsService(guides, bookings);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileEntity profile(UUID guideId, UUID userId) {
        GuideProfileEntity p = new GuideProfileEntity();
        p.setId(guideId);
        p.setUserId(userId);
        p.setCurrency("USD");
        return p;
    }

    @Test
    void getStats_returnsAssembledDto() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity p = profile(guideId, userId);
        p.setAvgRating(new BigDecimal("4.5"));
        p.setReviewCount(12);

        when(guides.findByUserId(userId)).thenReturn(Optional.of(p));
        when(bookings.sumGuideEarningsThisMonth(
                        eq(guideId), any(Instant.class), any(Instant.class)))
                .thenReturn(8400L);
        when(bookings.sumGuideUpcomingPayout(guideId)).thenReturn(4200L);

        GuideDashboardStatsResponse result = service().getStats(user(userId));

        assertEquals(new BigDecimal("4.5"), result.avgRating());
        assertEquals(12, result.reviewCount());
        assertEquals(8400L, result.earningsThisMonthCents());
        assertEquals(4200L, result.upcomingPayoutCents());
        assertEquals("USD", result.currency());
    }

    @Test
    void getStats_throwsNotFound_whenNoProfile() {
        UUID userId = UUID.randomUUID();
        when(guides.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().getStats(user(userId)));
    }

    @Test
    void getStats_defaultsCurrencyToUsd_whenProfileCurrencyIsNull() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity p = profile(guideId, userId);
        p.setCurrency(null);

        when(guides.findByUserId(userId)).thenReturn(Optional.of(p));
        when(bookings.sumGuideEarningsThisMonth(
                        eq(guideId), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(bookings.sumGuideUpcomingPayout(guideId)).thenReturn(0L);

        GuideDashboardStatsResponse result = service().getStats(user(userId));

        assertEquals("USD", result.currency());
    }

    @Test
    void getStats_forwardsNullAvgRating_whenNoReviewsYet() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity p = profile(guideId, userId);
        // avgRating is null by default; reviewCount is 0 by default

        when(guides.findByUserId(userId)).thenReturn(Optional.of(p));
        when(bookings.sumGuideEarningsThisMonth(
                        eq(guideId), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(bookings.sumGuideUpcomingPayout(guideId)).thenReturn(0L);

        GuideDashboardStatsResponse result = service().getStats(user(userId));

        assertNull(result.avgRating());
        assertEquals(0, result.reviewCount());
    }

    @Test
    void getStats_passesCorrectMonthBoundariesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity p = profile(guideId, userId);

        when(guides.findByUserId(userId)).thenReturn(Optional.of(p));
        // Capture the from/to instants and verify they are on month boundaries (day=1,
        // time=00:00Z).
        java.time.ZoneOffset utc = java.time.ZoneOffset.UTC;
        when(bookings.sumGuideEarningsThisMonth(
                        eq(guideId), any(Instant.class), any(Instant.class)))
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
                            // to must be exactly one month after from
                            assertEquals(
                                    zFrom.getMonth().plus(1).getValue() % 12 == 0
                                            ? 12
                                            : zFrom.getMonth().plus(1).getValue(),
                                    zTo.getMonthValue() == 1 ? 1 : zTo.getMonthValue());
                            return 0L;
                        });
        when(bookings.sumGuideUpcomingPayout(guideId)).thenReturn(0L);

        service().getStats(user(userId));
    }
}
