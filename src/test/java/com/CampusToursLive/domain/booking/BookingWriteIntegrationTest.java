package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
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
@Import(BookingService.class)
class BookingWriteIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired BookingService service;
    @Autowired BookingRepository bookings;
    @Autowired BookingStatusHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;

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
        g.setUniversityId(university.getId());
        g.setMajor("Computer Science");
        g.setApplicationStatus(GuideApplicationStatus.APPROVED);
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
                                "America/Los_Angeles",
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
        b.setDisplayTimezone("America/Los_Angeles");
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
