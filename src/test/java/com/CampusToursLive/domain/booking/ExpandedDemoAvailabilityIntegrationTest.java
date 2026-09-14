package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CTL-120 coverage for the broader demo schedule added after the initial bookable seed. These
 * checks exercise the same repository and services used by the BFF instead of only counting rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({BookingService.class, SlotGenerationService.class})
class ExpandedDemoAvailabilityIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SlotGenerationService slotService;
    @Autowired private BookingService bookingService;
    @Autowired private TourOfferingRepository offerings;
    @Autowired private UserRepository users;

    @Test
    void everyDemoGuideHasWeekdayAndWeekendRules() {
        Integer targetGuideCount =
                jdbc.queryForObject(
                        """
                        SELECT count(DISTINCT guide_profile.id)
                        FROM users guide_user
                        JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
                        WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
                          AND guide_profile.guide_status = 'VERIFIED'::guide_application_status
                        """,
                        Integer.class);
        List<GuideRuleCoverage> coverage =
                jdbc.query(
                        """
                        SELECT
                          guide_profile.id,
                          count(*) FILTER (
                            WHERE rule.day_of_week BETWEEN 1 AND 5
                              AND rule.start_local = '10:00'::time
                              AND rule.window_min = 240
                          ) AS weekday_mornings,
                          count(*) FILTER (
                            WHERE rule.day_of_week IN (0, 6)
                              AND rule.start_local = '11:00'::time
                              AND rule.window_min = 240
                          ) AS weekend_windows,
                          count(*) FILTER (
                            WHERE rule.day_of_week IN (0, 6)
                              AND rule.start_local = '11:00'::time
                              AND rule.window_min = 240
                              AND rule.effective_to IS NULL
                          ) AS open_ended_weekend_windows,
                          count(DISTINCT rule.day_of_week) AS covered_days
                        FROM users guide_user
                        JOIN guide_profiles guide_profile
                          ON guide_profile.user_id = guide_user.id
                        LEFT JOIN guide_availability_rules rule
                          ON rule.guide_id = guide_profile.id
                         AND rule.active = true
                        WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
                        GROUP BY guide_profile.id
                        ORDER BY guide_profile.id
                        """,
                        (rs, rowNum) ->
                                new GuideRuleCoverage(
                                        rs.getInt("weekday_mornings"),
                                        rs.getInt("weekend_windows"),
                                        rs.getInt("open_ended_weekend_windows"),
                                        rs.getInt("covered_days")));

        assertThat(coverage).hasSize(targetGuideCount);
        assertThat(coverage)
                .allSatisfy(
                        guide -> {
                            assertThat(guide.weekdayMornings()).isEqualTo(5);
                            assertThat(guide.weekendWindows()).isEqualTo(2);
                            assertThat(guide.openEndedWeekendWindows()).isEqualTo(2);
                            assertThat(guide.coveredDays()).isEqualTo(7);
                        });
    }

    @Test
    void everyDiscoverableOfferingProducesBookableSlots() {
        List<UUID> offeringIds =
                jdbc.queryForList(
                        """
                        SELECT offering.id
                        FROM tour_offerings offering
                        JOIN guide_profiles guide_profile ON guide_profile.id = offering.guide_id
                        JOIN universities university ON university.id = offering.university_id
                        WHERE offering.status = 'ACTIVE'::tour_status
                          AND guide_profile.guide_status = 'VERIFIED'::guide_application_status
                          AND university.status = 'ACTIVE'::university_status
                        ORDER BY offering.id
                        """,
                        UUID.class);

        assertThat(offeringIds).isNotEmpty();
        for (UUID offeringId : offeringIds) {
            TourOfferingEntity offering = offerings.findById(offeringId).orElseThrow();

            List<SlotResponse> slots = slotService.getBookableSlots(offering.getId(), null, null);

            assertThat(slots).isNotEmpty();
            assertThat(slots)
                    .allSatisfy(
                            slot ->
                                    assertThat(Duration.between(slot.startAt(), slot.endAt()))
                                            .isEqualTo(
                                                    Duration.ofMinutes(offering.getDurationMin())));
        }
    }

    @Test
    void weekendDateFilterReturnsViewerSelectableSaturdaySlots() {
        TourOfferingEntity offering = offeringBySlug("tour-1-1");
        ZoneId guideZone = ZoneId.of(settingTimezone(offering.getGuideId()));
        LocalDate saturday =
                LocalDate.now(guideZone).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));

        List<SlotResponse> slots =
                slotService.getBookableSlots(
                        offering.getId(), saturday.toString(), saturday.plusDays(1).toString());

        assertThat(slots).isNotEmpty();
        assertThat(slots)
                .allSatisfy(
                        slot -> {
                            ZonedDateTime localStart = slot.startAt().atZone(guideZone);
                            assertThat(localStart.toLocalDate()).isEqualTo(saturday);
                            assertThat(localStart.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
                            assertThat(localStart.getHour()).isBetween(11, 14);
                        });
    }

    @Test
    void bookingBlocksSameGuideAcrossOfferingsAndCancellationRestoresTheSlot() {
        TourOfferingEntity shortTour = offeringBySlug("tour-1-1");
        TourOfferingEntity alternateTour = offeringBySlug("tour-1-2");
        assertThat(alternateTour.getGuideId()).isEqualTo(shortTour.getGuideId());

        Instant safeStart = Instant.now().plus(Duration.ofHours(2));
        SlotResponse selected =
                slotService.getBookableSlots(shortTour.getId(), null, null).stream()
                        .filter(slot -> slot.startAt().isAfter(safeStart))
                        .findFirst()
                        .orElseThrow();
        assertThat(slotService.getBookableSlots(alternateTour.getId(), null, null))
                .extracting(SlotResponse::startAt)
                .contains(selected.startAt());

        UserEntity participant = users.saveAndFlush(participant());
        BookingDetailResponse booking =
                bookingService.createBooking(
                        participant,
                        new CreateBookingRequest(
                                shortTour.getId().toString(),
                                selected.startAt().toString(),
                                "CTL-120 cross-offering capacity test"));

        assertThat(slotService.getBookableSlots(alternateTour.getId(), null, null))
                .extracting(SlotResponse::startAt)
                .doesNotContain(selected.startAt());

        BookingDetailResponse cancelled =
                bookingService.cancelBooking(
                        participant,
                        UUID.fromString(booking.id()),
                        new CancelBookingRequest("Restore demo slot"));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(slotService.getBookableSlots(alternateTour.getId(), null, null))
                .extracting(SlotResponse::startAt)
                .contains(selected.startAt());
    }

    private TourOfferingEntity offeringBySlug(String slug) {
        return offerings.findAll().stream()
                .filter(offering -> slug.equals(offering.getSlug()))
                .findFirst()
                .orElseThrow();
    }

    private String settingTimezone(UUID guideId) {
        return jdbc.queryForObject(
                "SELECT timezone FROM guide_booking_settings WHERE guide_id = ?",
                String.class,
                guideId);
    }

    private static UserEntity participant() {
        UserEntity user = new UserEntity();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setOidcSubject("ctl-120-expanded-participant-" + id);
        user.setEmail("ctl-120-expanded-" + id + "@example.com");
        user.setDisplayName("CTL-120 Expanded Participant");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Chicago");
        return user;
    }

    private record GuideRuleCoverage(
            int weekdayMornings,
            int weekendWindows,
            int openEndedWeekendWindows,
            int coveredDays) {}
}
