package com.CampusToursLive.domain.guide;

import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.web.dto.GuideEarningsResponse;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the guide's earnings in a single read-only transaction. Both figures come from native
 * SQL aggregations on the bookings table joined through guide_profiles — no separate profile lookup
 * needed.
 */
@Service
public class GuideEarningsService {

    private final BookingRepository bookings;

    public GuideEarningsService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public GuideEarningsResponse getEarnings(UserEntity user) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant monthStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd =
                currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long earnedThisMonth =
                bookings.sumGuideEarningsThisMonthByUserId(user.getId(), monthStart, monthEnd);
        long upcomingPayout = bookings.sumGuideUpcomingPayoutByUserId(user.getId());

        return new GuideEarningsResponse(earnedThisMonth, upcomingPayout, "USD");
    }
}
