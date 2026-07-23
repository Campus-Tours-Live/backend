package com.CampusToursLive.domain.guide;

import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.GuideDashboardStatsResponse;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the three guide dashboard stats (rating, monthly earnings, upcoming payout) in a
 * single read-only transaction. The two earnings figures come from native SQL aggregations on the
 * bookings table; rating and review count are maintained directly on guide_profiles by a DB trigger
 * / nightly batch and are read here as-is.
 */
@Service
public class GuideEarningsService {

    private final GuideProfileRepository guides;
    private final BookingRepository bookings;

    public GuideEarningsService(GuideProfileRepository guides, BookingRepository bookings) {
        this.guides = guides;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public GuideDashboardStatsResponse getStats(UserEntity user) {
        GuideProfileEntity profile =
                guides.findByUserId(user.getId())
                        .orElseThrow(() -> new NotFoundException("No guide profile for this user"));

        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant monthStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd =
                currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long earnedThisMonth =
                bookings.sumGuideEarningsThisMonth(profile.getId(), monthStart, monthEnd);
        long upcomingPayout = bookings.sumGuideUpcomingPayout(profile.getId());

        return new GuideDashboardStatsResponse(
                profile.getAvgRating(),
                profile.getReviewCount(),
                earnedThisMonth,
                upcomingPayout,
                profile.getCurrency() != null ? profile.getCurrency() : "USD");
    }
}
