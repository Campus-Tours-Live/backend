package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CTL-120 migration coverage: V2 seeds the demo marketplace, while V3 makes those seeded offerings
 * actually bookable by adding guide settings, weekly rules, and materialized occurrences.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({BookingService.class, SlotGenerationService.class})
class SeededDemoAvailabilityIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SlotGenerationService slotService;
    @Autowired private BookingService bookingService;
    @Autowired private TourOfferingRepository offerings;
    @Autowired private UserRepository users;

    @Test
    void migrationSeedsSettingsRulesAndOccurrencesForEveryDemoGuide() {
        long seedGuides =
                count(
                        """
                        SELECT count(*)
                        FROM users guide_user
                        JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
                        WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
                          AND guide_profile.guide_status = 'VERIFIED'::guide_application_status
                        """);
        long settings =
                count(
                        """
                        SELECT count(*)
                        FROM guide_booking_settings settings
                        JOIN guide_profiles guide_profile ON guide_profile.id = settings.guide_id
                        JOIN users guide_user ON guide_user.id = guide_profile.user_id
                        WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
                        """);
        long minRules = ruleCoverage("min");
        long maxRules = ruleCoverage("max");
        long occurrences =
                count(
                        """
                        SELECT count(*)
                        FROM guide_availability_occurrences occurrence
                        JOIN guide_profiles guide_profile ON guide_profile.id = occurrence.guide_id
                        JOIN users guide_user ON guide_user.id = guide_profile.user_id
                        WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
                          AND occurrence.source_rule_id IS NOT NULL
                        """);

        assertThat(seedGuides).isGreaterThan(0);
        assertThat(settings).isEqualTo(seedGuides);
        assertThat(minRules).isEqualTo(5);
        assertThat(maxRules).isEqualTo(5);
        assertThat(occurrences).isGreaterThanOrEqualTo(seedGuides * 20);
    }

    @Test
    void seededDemoOfferingSlotCanBeUsedToCreateABooking() {
        TourOfferingEntity offering =
                offerings
                        .findDiscoverable(
                                null,
                                false,
                                List.of(TourTopic.GENERAL_CAMPUS),
                                "",
                                PageRequest.of(0, 20))
                        .getContent()
                        .stream()
                        .findFirst()
                        .orElseThrow();

        List<SlotResponse> slots = slotService.getBookableSlots(offering.getId(), null, null);
        assertThat(slots).isNotEmpty();

        SlotResponse selected = slots.get(0);
        assertThat(Duration.between(selected.startAt(), selected.endAt()))
                .isEqualTo(Duration.ofMinutes(offering.getDurationMin()));

        UserEntity participant = users.saveAndFlush(participant());
        BookingDetailResponse booking =
                bookingService.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(),
                                selected.startAt().toString(),
                                "CTL-120 seeded slot smoke test"));

        assertThat(booking.status()).isEqualTo("WAITING_FOR_GUIDE");
        assertThat(booking.offeringId()).isEqualTo(offering.getId().toString());
        assertThat(booking.scheduledAt()).isEqualTo(selected.startAt().toString());

        List<SlotResponse> remaining = slotService.getBookableSlots(offering.getId(), null, null);
        assertThat(remaining).extracting(SlotResponse::startAt).doesNotContain(selected.startAt());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long ruleCoverage(String aggregate) {
        Long value =
                jdbc.queryForObject(
                        """
                        SELECT %s(rule_count)
                        FROM (
                          SELECT count(*) AS rule_count
                          FROM guide_availability_rules rule
                          JOIN guide_profiles guide_profile ON guide_profile.id = rule.guide_id
                          JOIN users guide_user ON guide_user.id = guide_profile.user_id
                          WHERE guide_user.oidc_subject LIKE 'seed-guide-%%'
                            AND rule.active = true
                          GROUP BY rule.guide_id
                        ) coverage
                        """
                                .formatted(aggregate),
                        Long.class);
        return value == null ? 0 : value;
    }

    private static UserEntity participant() {
        UserEntity user = new UserEntity();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setOidcSubject("ctl-120-participant-" + id);
        user.setEmail("ctl-120-" + id + "@example.com");
        user.setDisplayName("CTL-120 Participant");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Chicago");
        return user;
    }
}
