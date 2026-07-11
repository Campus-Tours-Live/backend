package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceEntity;
import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
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
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * Persistence integration test for {@link SlotGenerationService} (CTL-54 Task 8) against a REAL
 * PostgreSQL (Testcontainers). Occurrences and held bookings are seeded directly (this task
 * consumes the already-materialized occurrences from Tasks 2/3/5b -- their own correctness is
 * covered there); this suite proves the slicing / booking-subtraction / notice-window math on top.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SlotGenerationServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    /** Fixed "now" -- occurrences in these tests sit a few days out, comfortably clear of it. */
    private static final Instant FIXED_NOW = Instant.parse("2026-07-11T00:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired private TourOfferingRepository offerings;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private BookingRepository bookings;
    @Autowired private GuideBookingSettingsRepository settingsRepo;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;

    private SlotGenerationService service;
    private UUID universityId;
    private GuideProfileEntity guide;

    @BeforeEach
    void setUp() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        universityId = university.getId();

        UserEntity guideUser = users.save(user("Jane Guide"));
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(guideUser.getId());
        g.setUniversityId(universityId);
        g.setMajor("Computer Science");
        g.setApplicationStatus(GuideApplicationStatus.APPROVED);
        guide = guides.save(g);

        service =
                new SlotGenerationService(
                        offerings, occurrences, bookings, settingsRepo, FIXED_CLOCK);
    }

    // ---------------------------------------------------------------------
    // Slicing.
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_slicesOccurrenceIntoBackToBackSlots_exactDivision() {
        TourOfferingEntity offering = offering(60);
        Instant start = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        occurrence(start, start.plus(4, java.time.temporal.ChronoUnit.HOURS)); // [10:00,14:00)

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).hasSize(4);
        assertThat(slots)
                .extracting(SlotResponse::startAt)
                .containsExactly(
                        start,
                        start.plus(1, java.time.temporal.ChronoUnit.HOURS),
                        start.plus(2, java.time.temporal.ChronoUnit.HOURS),
                        start.plus(3, java.time.temporal.ChronoUnit.HOURS));
        for (SlotResponse s : slots) {
            assertThat(Duration.between(s.startAt(), s.endAt())).isEqualTo(Duration.ofMinutes(60));
            assertThat(s.endAt())
                    .isBeforeOrEqualTo(start.plus(4, java.time.temporal.ChronoUnit.HOURS));
        }
    }

    @Test
    void getBookableSlots_dropsPartialTail_whenDurationDoesNotDivideEvenly() {
        TourOfferingEntity offering = offering(90);
        Instant start = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        occurrence(
                start,
                start.plus(4, java.time.temporal.ChronoUnit.HOURS)); // 240 min / 90 = 2 + 60 tail

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startAt()).isEqualTo(start);
        assertThat(slots.get(0).endAt())
                .isEqualTo(start.plus(90, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(slots.get(1).startAt())
                .isEqualTo(start.plus(90, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(slots.get(1).endAt())
                .isEqualTo(start.plus(180, java.time.temporal.ChronoUnit.MINUTES));
        // The remaining 60-minute tail [180,240) is shorter than 90 min and must not appear.
        assertThat(slots)
                .noneMatch(
                        s ->
                                s.startAt()
                                        .equals(
                                                start.plus(
                                                        180,
                                                        java.time.temporal.ChronoUnit.MINUTES)));
    }

    // ---------------------------------------------------------------------
    // Booking subtraction (reserved interval, with buffer).
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_removesSlotsWhoseReservedIntervalOverlapsAHeldBooking() {
        TourOfferingEntity offering = offering(60);
        Instant start = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS); // 09:00
        // A wider occurrence so slots beyond the buffer-affected neighbors are observable.
        occurrence(
                start,
                start.plus(6, java.time.temporal.ChronoUnit.HOURS)); // [09:00,15:00): 6 slots
        Instant heldStart = start.plus(3, java.time.temporal.ChronoUnit.HOURS); // 12:00
        Instant heldEnd = start.plus(4, java.time.temporal.ChronoUnit.HOURS); // 13:00
        heldBooking(heldStart, heldEnd, BookingStatus.CONFIRMED);

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        // 12:00 (the held slot itself) is gone, and so are its immediate neighbors 11:00/13:00 --
        // the 15-min post-tour buffer means a slot ending exactly when another starts (or
        // starting exactly when another's buffer is still running) is NOT actually bookable.
        assertThat(slots)
                .extracting(SlotResponse::startAt)
                .doesNotContain(
                        start.plus(2, java.time.temporal.ChronoUnit.HOURS), // 11:00
                        start.plus(3, java.time.temporal.ChronoUnit.HOURS), // 12:00 (held)
                        start.plus(4, java.time.temporal.ChronoUnit.HOURS)); // 13:00
        // Slots further out (with a clean 15-min+ gap) remain free.
        assertThat(slots)
                .extracting(SlotResponse::startAt)
                .contains(
                        start, // 09:00
                        start.plus(1, java.time.temporal.ChronoUnit.HOURS), // 10:00
                        start.plus(5, java.time.temporal.ChronoUnit.HOURS)); // 14:00
    }

    @Test
    void getBookableSlots_ignoresHeldBooking_forADifferentGuide() {
        TourOfferingEntity offering = offering(60);
        Instant start = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        occurrence(start, start.plus(2, java.time.temporal.ChronoUnit.HOURS));

        UserEntity otherGuideUser = users.save(user("Other Guide"));
        GuideProfileEntity otherGuide = new GuideProfileEntity();
        otherGuide.setId(UUID.randomUUID());
        otherGuide.setUserId(otherGuideUser.getId());
        otherGuide.setUniversityId(universityId);
        otherGuide.setMajor("Biology");
        otherGuide.setApplicationStatus(GuideApplicationStatus.APPROVED);
        guides.save(otherGuide);

        BookingEntity elsewhere = new BookingEntity();
        elsewhere.setId(UUID.randomUUID());
        elsewhere.setBookingNumber("BK-OTHERGD01");
        elsewhere.setParticipantUserId(users.save(user("Some Participant")).getId());
        elsewhere.setGuideId(otherGuide.getId());
        elsewhere.setTourOfferingId(offering.getId());
        elsewhere.setUniversityId(universityId);
        elsewhere.setStatus(BookingStatus.CONFIRMED);
        elsewhere.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        elsewhere.setScheduledStartAt(start);
        elsewhere.setScheduledEndAt(start.plus(1, java.time.temporal.ChronoUnit.HOURS));
        elsewhere.setReservedStartAt(start);
        elsewhere.setReservedEndAt(start.plus(75, java.time.temporal.ChronoUnit.MINUTES));
        elsewhere.setBasePriceCents(1000L);
        elsewhere.setTotalCents(1000L);
        elsewhere.setPlatformFeeCents(0L);
        elsewhere.setGuideAmountCents(1000L);
        elsewhere.setCurrency("USD");
        bookings.saveAndFlush(elsewhere);

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        // The other guide's booking at the identical time must not affect THIS guide's slots.
        assertThat(slots).extracting(SlotResponse::startAt).contains(start);
    }

    // ---------------------------------------------------------------------
    // Notice window.
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_excludesSlotsStartingBeforeMinNotice() {
        TourOfferingEntity offering = offering(60);
        GuideBookingSettingsEntity settings = new GuideBookingSettingsEntity();
        settings.setGuideId(guide.getId());
        settings.setMinNoticeMin((int) Duration.ofHours(72).toMinutes()); // 3-day min notice
        settingsRepo.saveAndFlush(settings);

        // One occurrence starting only 1 day out (too soon) and one starting 5 days out (fine).
        Instant tooSoon = FIXED_NOW.plus(1, java.time.temporal.ChronoUnit.DAYS);
        occurrence(tooSoon, tooSoon.plus(1, java.time.temporal.ChronoUnit.HOURS));
        Instant fineLater = FIXED_NOW.plus(5, java.time.temporal.ChronoUnit.DAYS);
        occurrence(fineLater, fineLater.plus(1, java.time.temporal.ChronoUnit.HOURS));

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).extracting(SlotResponse::startAt).containsExactly(fineLater);
    }

    // ---------------------------------------------------------------------
    // Max advance.
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_excludesSlotsBeyondMaxAdvance() {
        TourOfferingEntity offering = offering(60);
        GuideBookingSettingsEntity settings = new GuideBookingSettingsEntity();
        settings.setGuideId(guide.getId());
        settings.setMaxAdvanceDays(10);
        settingsRepo.saveAndFlush(settings);

        Instant withinAdvance = FIXED_NOW.plus(5, java.time.temporal.ChronoUnit.DAYS);
        occurrence(withinAdvance, withinAdvance.plus(1, java.time.temporal.ChronoUnit.HOURS));
        Instant tooFarOut = FIXED_NOW.plus(20, java.time.temporal.ChronoUnit.DAYS);
        occurrence(tooFarOut, tooFarOut.plus(1, java.time.temporal.ChronoUnit.HOURS));

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).extracting(SlotResponse::startAt).containsExactly(withinAdvance);
    }

    // ---------------------------------------------------------------------
    // DST day: purely a UTC-arithmetic sanity check (the projection already resolved DST -- Task
    // 2) -- slicing across the transition date must still be gap/overlap/duplicate free.
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_sliceAcrossDstTransitionDate_isContiguousNoGapOrOverlap() {
        TourOfferingEntity offering = offering(60);
        // 2026-03-08 is a US spring-forward Sunday; the occurrence's UTC bounds are exactly what
        // the Task 2 projection would already have produced -- this suite only proves slicing
        // does not introduce a gap/overlap/duplicate on that date. Use a clock fixed a week
        // BEFORE that date (FIXED_NOW/FIXED_CLOCK above is 2026-07-11, well after it, which would
        // fail the max-advance check instead of exercising slicing).
        Instant beforeDst = Instant.parse("2026-03-01T00:00:00Z");
        SlotGenerationService dstService =
                new SlotGenerationService(
                        offerings,
                        occurrences,
                        bookings,
                        settingsRepo,
                        Clock.fixed(beforeDst, ZoneOffset.UTC));
        Instant dstDayStart = Instant.parse("2026-03-08T09:00:00Z");
        occurrence(dstDayStart, dstDayStart.plus(4, java.time.temporal.ChronoUnit.HOURS));

        List<SlotResponse> slots = dstService.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).hasSize(4);
        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).startAt()).isEqualTo(slots.get(i - 1).endAt());
        }
        assertThat(slots.get(0).startAt()).isEqualTo(dstDayStart);
        assertThat(slots.get(slots.size() - 1).endAt())
                .isEqualTo(dstDayStart.plus(4, java.time.temporal.ChronoUnit.HOURS));
    }

    // ---------------------------------------------------------------------
    // Empty / not-found.
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_returnsEmptyList_whenOfferingHasNoOccurrences() {
        TourOfferingEntity offering = offering(60);

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), null, null);

        assertThat(slots).isEmpty();
    }

    @Test
    void getBookableSlots_throwsNotFound_whenOfferingDoesNotExist() {
        assertThatThrownBy(() -> service.getBookableSlots(UUID.randomUUID(), null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getBookableSlots_throwsNotFound_whenOfferingIsNotActive() {
        TourOfferingEntity offering = offering(60);
        offering.setStatus(TourStatus.DRAFT);
        offerings.saveAndFlush(offering);
        Instant start = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        occurrence(start, start.plus(1, java.time.temporal.ChronoUnit.HOURS));

        assertThatThrownBy(() -> service.getBookableSlots(offering.getId(), null, null))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------------
    // Window filter (from/to narrows considered occurrences).
    // ---------------------------------------------------------------------

    @Test
    void getBookableSlots_windowFilter_narrowsToIntersectingOccurrences() {
        TourOfferingEntity offering = offering(60);
        Instant near = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        Instant far = FIXED_NOW.plus(10, java.time.temporal.ChronoUnit.DAYS);
        occurrence(near, near.plus(1, java.time.temporal.ChronoUnit.HOURS));
        occurrence(far, far.plus(1, java.time.temporal.ChronoUnit.HOURS));

        String from = near.minus(1, java.time.temporal.ChronoUnit.DAYS).toString().substring(0, 10);
        String to = near.plus(1, java.time.temporal.ChronoUnit.DAYS).toString().substring(0, 10);

        List<SlotResponse> slots = service.getBookableSlots(offering.getId(), from, to);

        assertThat(slots).extracting(SlotResponse::startAt).containsExactly(near);
    }

    @Test
    void getBookableSlots_throwsValidation_whenToIsNotAfterFrom() {
        TourOfferingEntity offering = offering(60);
        Instant near = FIXED_NOW.plus(3, java.time.temporal.ChronoUnit.DAYS);
        occurrence(near, near.plus(1, java.time.temporal.ChronoUnit.HOURS));

        // to == from -- the half-open [from, to) window is empty, which is the same class of
        // caller error as to < from (both mean "no window"), so the service rejects it as a 422
        // rather than silently returning zero slots.
        String from = "2026-07-20";
        String to = "2026-07-20";

        assertThatThrownBy(() -> service.getBookableSlots(offering.getId(), from, to))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("to must be after from");
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private TourOfferingEntity offering(int durationMin) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setUniversityId(universityId);
        o.setTitle("Campus Walk");
        o.setSlug("campus-walk-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(durationMin);
        o.setPriceCents(5000L);
        o.setStatus(TourStatus.ACTIVE);
        return offerings.saveAndFlush(o);
    }

    private GuideAvailabilityOccurrenceEntity occurrence(Instant start, Instant end) {
        GuideAvailabilityOccurrenceEntity o = new GuideAvailabilityOccurrenceEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setDuringStartAt(start);
        o.setDuringEndAt(end);
        o.setGeneratedAt(Instant.now());
        return occurrences.saveAndFlush(o);
    }

    private BookingEntity heldBooking(
            Instant scheduledStart, Instant scheduledEnd, BookingStatus status) {
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-HELD" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        b.setParticipantUserId(users.save(user("Held Participant")).getId());
        b.setGuideId(guide.getId());
        TourOfferingEntity o =
                offering((int) Duration.between(scheduledStart, scheduledEnd).toMinutes());
        b.setTourOfferingId(o.getId());
        b.setUniversityId(universityId);
        b.setStatus(status);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(scheduledStart);
        b.setScheduledEndAt(scheduledEnd);
        b.setReservedStartAt(scheduledStart);
        b.setReservedEndAt(scheduledEnd.plus(BookingService.RESERVED_BUFFER_AFTER));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(5000L);
        b.setCurrency("USD");
        return bookings.saveAndFlush(b);
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("sg-" + UUID.randomUUID());
        u.setEmail("sg-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }
}
