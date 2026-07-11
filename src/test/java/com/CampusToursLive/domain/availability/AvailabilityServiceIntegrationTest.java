package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistence integration test for {@link AvailabilityService#rematerialize(UUID)} against a REAL
 * PostgreSQL (Testcontainers) — the only way to exercise the wholesale delete+insert, the {@code
 * excl_guide_occurrence_no_overlap} GIST backstop on a real INSERT, and the V5 DST-notice table.
 * Mirrors {@code GuideAvailabilityOccurrenceRepositoryTest} / {@code BookingWriteIntegrationTest}.
 *
 * <p>The service is constructed with a FIXED clock (today = {@code 2026-03-01}) so the rolling
 * horizon is deterministic and includes the US 2026 spring-forward Sunday ({@code 2026-03-08}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final String LA = "America/Los_Angeles";
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 1);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private AvailabilityExceptionRepository exceptions;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideAvailabilityDstNoticeRepository dstNotices;
    @Autowired private GuideBookingSettingsRepository settings;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;
    @Autowired private TourOfferingRepository offerings;
    @Autowired private BookingRepository bookings;

    private AvailabilityService service;
    private UUID guideId;
    private UUID universityId;

    @BeforeEach
    void setUp() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        universityId = university.getId();

        UserEntity guideUser = users.save(user("Jane Guide"));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setUniversityId(universityId);
        guide.setMajor("Computer Science");
        guide.setApplicationStatus(GuideApplicationStatus.APPROVED);
        guides.save(guide);
        guideId = guide.getId();

        service =
                new AvailabilityService(
                        rules, exceptions, occurrences, dstNotices, settings, FIXED_CLOCK);
    }

    @Test
    void rematerialize_persistsOccurrencesMatchingTheProjection() {
        // Two rules: a Monday 10:00-12:00 window recurring across three effective Mondays, plus a
        // single-day Wednesday 14:00-15:00 window. The persisted set must equal project(...).
        rules.save(
                rule(
                        1,
                        LocalTime.of(10, 0),
                        120,
                        LA,
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 16)));
        rules.save(
                rule(
                        3,
                        LocalTime.of(14, 0),
                        60,
                        LA,
                        LocalDate.of(2026, 3, 4),
                        LocalDate.of(2026, 3, 4)));

        service.rematerialize(guideId);

        ProjectionResult expected =
                AvailabilityProjection.project(
                        rules.findByGuideId(guideId), exceptions.findByGuideId(guideId), horizon());

        List<GuideAvailabilityOccurrenceEntity> persisted =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId);

        assertThat(persisted)
                .extracting(
                        GuideAvailabilityOccurrenceEntity::getDuringStartAt,
                        GuideAvailabilityOccurrenceEntity::getDuringEndAt)
                .containsExactlyElementsOf(
                        expected.intervals().stream()
                                .map(
                                        i ->
                                                org.assertj.core.groups.Tuple.tuple(
                                                        i.startAt(), i.endAt()))
                                .toList());
        // Disjoint + ascending (mirrors the projection's invariant, now on the DB rows).
        for (int i = 1; i < persisted.size(); i++) {
            assertThat(persisted.get(i).getDuringStartAt())
                    .isAfterOrEqualTo(persisted.get(i - 1).getDuringEndAt());
        }
        assertThat(persisted).isNotEmpty();
    }

    @Test
    void rematerialize_isIdempotent() {
        rules.save(
                rule(
                        1,
                        LocalTime.of(9, 0),
                        60,
                        LA,
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 16)));

        service.rematerialize(guideId);
        List<Instant> firstStarts =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .map(GuideAvailabilityOccurrenceEntity::getDuringStartAt)
                        .toList();

        // Second call must produce the identical set with no duplicates and no GIST violation.
        assertThatCode(() -> service.rematerialize(guideId)).doesNotThrowAnyException();

        List<Instant> secondStarts =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .map(GuideAvailabilityOccurrenceEntity::getDuringStartAt)
                        .toList();

        assertThat(secondStarts).isEqualTo(firstStarts).isNotEmpty();
    }

    @Test
    void rematerialize_leavesExistingBookingsUntouched() {
        TourOfferingEntity offering = seedOffering();
        UserEntity participant = users.save(user("Pat Participant"));
        Instant bookingStart =
                Instant.parse("2026-03-05T18:00:00Z"); // inside the horizon, distinct from windows
        BookingEntity booking =
                bookings.saveAndFlush(heldBooking(participant.getId(), offering, bookingStart));
        Instant reservedEndBefore = booking.getReservedEndAt();

        rules.save(
                rule(
                        1,
                        LocalTime.of(9, 0),
                        60,
                        LA,
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 2)));
        service.rematerialize(guideId);

        BookingEntity after = bookings.findById(booking.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(after.getScheduledStartAt()).isEqualTo(bookingStart);
        assertThat(after.getReservedEndAt()).isEqualTo(reservedEndBefore);
    }

    @Test
    void rematerialize_exceptionsWithoutRules_materializesViaSettingsTimezone() {
        // A guide with a settings row (timezone) and a one-off ADDITIONAL exception but NO rules
        // must materialize the exception's window via the settings timezone, NOT throw.
        settings.save(bookingSettings(guideId, LA));
        exceptions.save(
                exception(
                        LocalDate.of(2026, 3, 4),
                        AvailabilityExceptionKind.ADDITIONAL,
                        LocalTime.of(10, 0),
                        60));

        assertThatCode(() -> service.rematerialize(guideId)).doesNotThrowAnyException();

        List<GuideAvailabilityOccurrenceEntity> persisted =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId);
        // 10:00 America/Los_Angeles on 2026-03-04 (PST, -08:00, before the 03-08 transition).
        assertThat(persisted)
                .extracting(
                        GuideAvailabilityOccurrenceEntity::getDuringStartAt,
                        GuideAvailabilityOccurrenceEntity::getDuringEndAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                Instant.parse("2026-03-04T18:00:00Z"),
                                Instant.parse("2026-03-04T19:00:00Z")));
    }

    @Test
    void rematerialize_persistsDstNoticeForSpringForwardDay() {
        // Sunday 2026-03-08 02:30 America/Los_Angeles falls in the spring-forward gap
        // (02:00->03:00),
        // so the projection flags that day -> a guide_availability_dst_notices row must be written.
        rules.save(
                rule(
                        0,
                        LocalTime.of(2, 30),
                        60,
                        LA,
                        LocalDate.of(2026, 3, 8),
                        LocalDate.of(2026, 3, 8)));

        service.rematerialize(guideId);

        assertThat(dstNotices.findByGuideId(guideId))
                .extracting(GuideAvailabilityDstNoticeEntity::getAdjustedDate)
                .containsExactly(LocalDate.of(2026, 3, 8));
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private static AvailabilityHorizon horizon() {
        return new AvailabilityHorizon(
                FIXED_TODAY, FIXED_TODAY.plusDays(AvailabilityService.HORIZON_DAYS));
    }

    private TourOfferingEntity seedOffering() {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guideId);
        o.setUniversityId(universityId);
        o.setTitle("Campus Walk");
        o.setSlug("campus-walk-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        o.setStatus(TourStatus.ACTIVE);
        return offerings.save(o);
    }

    private BookingEntity heldBooking(
            UUID participantUserId, TourOfferingEntity offering, Instant start) {
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participantUserId);
        b.setGuideId(guideId);
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(start.plus(60, ChronoUnit.MINUTES));
        b.setReservedStartAt(start);
        b.setReservedEndAt(start.plus(75, ChronoUnit.MINUTES));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(5000L);
        b.setCurrency("USD");
        return b;
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("it-" + UUID.randomUUID());
        u.setEmail("it-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone(LA);
        return u;
    }

    private GuideAvailabilityRuleEntity rule(
            int dayOfWeek,
            LocalTime startLocal,
            int windowMin,
            String timezone,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        GuideAvailabilityRuleEntity r = new GuideAvailabilityRuleEntity();
        r.setId(UUID.randomUUID());
        r.setGuideId(guideId);
        r.setDayOfWeek((short) dayOfWeek);
        r.setStartLocal(startLocal);
        r.setWindowMin(windowMin);
        r.setTimezone(timezone);
        r.setEffectiveFrom(effectiveFrom);
        r.setEffectiveTo(effectiveTo);
        r.setActive(true);
        return r;
    }

    private AvailabilityExceptionEntity exception(
            LocalDate exceptionDate,
            AvailabilityExceptionKind kind,
            LocalTime startLocal,
            int windowMin) {
        AvailabilityExceptionEntity e = new AvailabilityExceptionEntity();
        e.setId(UUID.randomUUID());
        e.setGuideId(guideId);
        e.setExceptionDate(exceptionDate);
        e.setKind(kind);
        e.setStartLocal(startLocal);
        e.setWindowMin(windowMin);
        return e;
    }

    private static GuideBookingSettingsEntity bookingSettings(UUID guideId, String timezone) {
        GuideBookingSettingsEntity s = new GuideBookingSettingsEntity();
        s.setGuideId(guideId);
        s.setTimezone(timezone);
        return s;
    }
}
