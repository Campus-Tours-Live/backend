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
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Booking-write integration test against a REAL PostgreSQL (Testcontainers). Exercises what the
 * Mockito tests can't: the insert ORDER inside createBooking (the booking row must be flushed
 * before the booking_status_history row whose FK references it — a deferred insert here FK-violates
 * on every create), the PG enum bindings on a real INSERT, and the {@code excl_guide_no_overlap}
 * exclusion constraint. Requires a running Docker daemon (same as UniversityRepositoryTest).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({BookingService.class, SlotGenerationService.class})
class BookingWriteIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired BookingService service;
    @Autowired SlotGenerationService slotService;
    @Autowired BookingRepository bookings;
    @Autowired BookingStatusHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;
    @Autowired GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired GuideBookingSettingsRepository settingsRepo;

    private UserEntity participant;
    private GuideProfileEntity guide;
    private TourOfferingEntity offering;

    @BeforeEach
    void seedGraph() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();

        participant = users.save(user("Pat Participant"));
        UserEntity guideUser = users.save(user("Jane Guide"));

        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(guideUser.getId());
        g.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guide = guides.save(g);

        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setUniversityId(university.getId());
        o.setTitle("Campus Walk");
        o.setSlug("campus-walk-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        o.setStatus(TourStatus.ACTIVE);
        offering = offerings.save(o);

        // CTL-54 Task 6: booking-create now requires the scheduled interval to be CONTAINED by a
        // materialized availability occurrence for the guide. Seed a wide window (well beyond the
        // 30-day max-advance this suite books within) so every pre-existing test in this class --
        // none of which is about availability -- keeps passing.
        occurrences.saveAndFlush(
                occurrence(
                        Instant.now().minus(1, ChronoUnit.DAYS),
                        Instant.now().plus(60, ChronoUnit.DAYS)));
    }

    /** An availability occurrence for this test's guide covering [start, end). */
    private GuideAvailabilityOccurrenceEntity occurrence(Instant start, Instant end) {
        GuideAvailabilityOccurrenceEntity o = new GuideAvailabilityOccurrenceEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setDuringStartAt(start);
        o.setDuringEndAt(end);
        o.setGeneratedAt(Instant.now());
        return o;
    }

    @Test
    void createBooking_persistsBookingBeforeAuditRow_againstRealPostgres() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        BookingDetailResponse resp =
                service.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(),
                                start.toString(),
                                "meet at the fountain"));

        BookingEntity saved = bookings.findById(UUID.fromString(resp.id())).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        assertThat(saved.getBookingNumber()).startsWith("BK-");
        assertThat(saved.getReservedEndAt())
                .isEqualTo(saved.getScheduledEndAt().plus(15, ChronoUnit.MINUTES));

        List<BookingStatusHistoryEntity> trail =
                history.findByBookingIdOrderByCreatedAtAsc(saved.getId());
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getPreviousStatus()).isNull();
        assertThat(trail.get(0).getNewStatus()).isEqualTo(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        assertThat(trail.get(0).getActorType()).isEqualTo(BookingActor.PARTICIPANT);
    }

    @Test
    void createBooking_noAvailabilityOccurrenceAtAll_isRejected() {
        occurrences.deleteAllInBatch(); // guide has no materialized availability whatsoever
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        assertThatThrownBy(
                        () ->
                                service.createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offering.getId().toString(),
                                                start.toString(),
                                                null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage(BookingService.OUTSIDE_AVAILABILITY_MESSAGE);
    }

    @Test
    void createBooking_occurrenceExactlyMatchesScheduledInterval_isContained_andSucceeds() {
        occurrences.deleteAllInBatch();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // The offering is 60 minutes; an occurrence exactly [start, start+60) must contain it
        // (boundary: occurrence == scheduled interval, not a strict superset).
        occurrences.saveAndFlush(occurrence(start, start.plus(60, ChronoUnit.MINUTES)));

        BookingDetailResponse resp =
                service.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(), start.toString(), null));

        assertThat(resp.status()).isEqualTo("WAITING_FOR_GUIDE");
    }

    @Test
    void createBooking_scheduledIntervalExtendsPastOccurrenceEnd_isRejected() {
        occurrences.deleteAllInBatch();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // The occurrence covers only 59 of the 60 scheduled minutes -- the booking's scheduled end
        // is one minute past where the guide's availability ends, so containment must fail.
        occurrences.saveAndFlush(occurrence(start, start.plus(59, ChronoUnit.MINUTES)));

        assertThatThrownBy(
                        () ->
                                service.createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offering.getId().toString(),
                                                start.toString(),
                                                null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage(BookingService.OUTSIDE_AVAILABILITY_MESSAGE);
    }

    @Test
    void overlappingGuideReservation_isRejectedByExclusionConstraint() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        UserEntity otherParticipant = users.save(user("Other Participant"));

        bookings.saveAndFlush(heldBooking(participant.getId(), start));
        // Same guide, second participant, starting mid-way through the reserved interval →
        // excl_guide_no_overlap must reject it at flush.
        assertThatThrownBy(
                        () ->
                                bookings.saveAndFlush(
                                        heldBooking(
                                                otherParticipant.getId(),
                                                start.plus(30, ChronoUnit.MINUTES))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── CTL-54 design-gap fix: createBooking honors the guide's OWN settings, and agrees with
    // SlotGenerationService about what is bookable (the "listed-but-unbookable" bug this closes)
    // ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void createBooking_customMaxAdvanceDays_slotGenListsIt_andCreateAcceptsIt() {
        occurrences.deleteAllInBatch(); // replace seedGraph()'s 60-day-wide occurrence
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setGuideId(guide.getId());
        guideSettings.setMaxAdvanceDays(180);
        settingsRepo.saveAndFlush(guideSettings);

        // 90 days out -- 422'd by the OLD hardcoded 30-day MAX_ADVANCE, even though this guide's
        // own settings (and therefore GET /offerings/{id}/slots) already allowed it.
        Instant start = Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        occurrences.saveAndFlush(
                occurrence(start.minus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS)));

        List<SlotResponse> slots = slotService.getBookableSlots(offering.getId(), null, null);
        assertThat(slots).extracting(SlotResponse::startAt).contains(start);

        BookingDetailResponse resp =
                service.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(), start.toString(), null));
        assertThat(resp.status()).isEqualTo("WAITING_FOR_GUIDE");
    }

    @Test
    void createBooking_customMinNoticeMin_slotGenExcludesIt_andCreateRejectsIt() {
        occurrences.deleteAllInBatch();
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setGuideId(guide.getId());
        guideSettings.setMinNoticeMin((int) Duration.ofHours(48).toMinutes());
        settingsRepo.saveAndFlush(guideSettings);

        // 25h out -- ACCEPTED by the OLD hardcoded 24h MIN_NOTICE, even though this guide's own
        // settings (and therefore GET /offerings/{id}/slots) require 48h and would not list it.
        Instant start = Instant.now().plus(25, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        occurrences.saveAndFlush(
                occurrence(start.minus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS)));

        List<SlotResponse> slots = slotService.getBookableSlots(offering.getId(), null, null);
        assertThat(slots).extracting(SlotResponse::startAt).doesNotContain(start);

        assertThatThrownBy(
                        () ->
                                service.createBooking(
                                        participant,
                                        new CreateBookingRequest(
                                                offering.getId().toString(),
                                                start.toString(),
                                                null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createBooking_bufferBeforeMin_reservesTimeBeforeScheduledStart() {
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setGuideId(guide.getId());
        guideSettings.setBufferBeforeMin(30);
        settingsRepo.saveAndFlush(guideSettings);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingDetailResponse resp =
                service.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(), start.toString(), null));

        BookingEntity saved = bookings.findById(UUID.fromString(resp.id())).orElseThrow();
        assertThat(saved.getReservedStartAt()).isEqualTo(start.minus(30, ChronoUnit.MINUTES));
        // bufferAfterMin was left at its schema default (15) -- unaffected by the before-buffer.
        assertThat(saved.getReservedEndAt())
                .isEqualTo(saved.getScheduledEndAt().plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void beforeBufferOverlap_isRejectedByExclusionConstraint_whenGuideHasBufferBeforeMin() {
        GuideBookingSettingsEntity guideSettings = new GuideBookingSettingsEntity();
        guideSettings.setGuideId(guide.getId());
        guideSettings.setBufferBeforeMin(30);
        settingsRepo.saveAndFlush(guideSettings);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingDetailResponse resp =
                service.createBooking(
                        participant,
                        new CreateBookingRequest(
                                offering.getId().toString(), start.toString(), null));
        BookingEntity a = bookings.findById(UUID.fromString(resp.id())).orElseThrow();
        assertThat(a.getReservedStartAt()).isEqualTo(start.minus(30, ChronoUnit.MINUTES));

        UserEntity otherParticipant = users.save(user("Other Participant"));
        // A second, independently-scheduled booking [start-40, start-20) has its OWN (default,
        // buffer-before=0) reserved interval [start-40, start-5) -- which collides with A's
        // 30-min before-buffer [start-30, start): the exclusion constraint must reject it.
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-BEFBUF01");
        b.setParticipantUserId(otherParticipant.getId());
        b.setGuideId(guide.getId());
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start.minus(40, ChronoUnit.MINUTES));
        b.setScheduledEndAt(start.minus(20, ChronoUnit.MINUTES));
        b.setReservedStartAt(start.minus(40, ChronoUnit.MINUTES));
        b.setReservedEndAt(start.minus(5, ChronoUnit.MINUTES));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(5000L);
        b.setCurrency("USD");

        assertThatThrownBy(() -> bookings.saveAndFlush(b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("it-" + UUID.randomUUID());
        u.setEmail("it-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }

    /** A CONFIRMED 60-min booking for this test's guide/offering with the 15-min buffer. */
    private BookingEntity heldBooking(UUID participantUserId, Instant start) {
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participantUserId);
        b.setGuideId(guide.getId());
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
}
