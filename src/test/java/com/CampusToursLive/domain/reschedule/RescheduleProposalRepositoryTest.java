package com.CampusToursLive.domain.reschedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingActor;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reschedule-proposal repository integration test against a REAL PostgreSQL (Testcontainers).
 * Exercises what H2/Mockito can't: the {@code reschedule_status} + {@code booking_actor} PG enums
 * round-tripping through JPA on a real INSERT, the DB-owned {@code created_at}/{@code updated_at}
 * timestamps, and the partial unique index {@code uq_reschedule_active} that guards the "one active
 * proposal per booking" invariant. Requires a running Docker daemon (same as the other repo tests).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RescheduleProposalRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired TestEntityManager em;
    @Autowired RescheduleProposalRepository proposals;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;

    private BookingEntity booking;

    @BeforeEach
    void seedBooking() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();

        UserEntity participant = users.save(user("Pat Participant"));
        UserEntity guideUser = users.save(user("Jane Guide"));

        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(guideUser.getId());
        g.setUniversityId(university.getId());
        g.setMajor("Computer Science");
        g.setApplicationStatus(GuideApplicationStatus.APPROVED);
        GuideProfileEntity guide = guides.save(g);

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
        TourOfferingEntity offering = offerings.save(o);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participant.getId());
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
        booking = bookings.saveAndFlush(b);
    }

    @Test
    void persistsProposal_roundTrippingPgEnumsAndDbTimestamps() {
        RescheduleProposalEntity saved =
                proposals.saveAndFlush(pending(BookingActor.PARTICIPANT, booking.getId()));

        // Drop the first-level cache so findById re-reads the row (and its DB-owned timestamps).
        em.clear();
        RescheduleProposalEntity read = proposals.findById(saved.getId()).orElseThrow();
        assertThat(read.getStatus()).isEqualTo(RescheduleStatus.PENDING_COUNTERPARTY);
        assertThat(read.getRequestedBy()).isEqualTo(BookingActor.PARTICIPANT);
        assertThat(read.getBookingId()).isEqualTo(booking.getId());
        // created_at / updated_at are DB-owned (default now() + trigger) — proves they are
        // populated.
        assertThat(read.getCreatedAt()).isNotNull();
        assertThat(read.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByBookingIdAndStatus_returnsTheActiveProposal() {
        RescheduleProposalEntity saved =
                proposals.saveAndFlush(pending(BookingActor.GUIDE, booking.getId()));

        assertThat(
                        proposals.findByBookingIdAndStatus(
                                booking.getId(), RescheduleStatus.PENDING_COUNTERPARTY))
                .get()
                .satisfies(p -> assertThat(p.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void secondActiveProposalForSameBooking_isRejectedByPartialUniqueIndex() {
        proposals.saveAndFlush(pending(BookingActor.PARTICIPANT, booking.getId()));

        // uq_reschedule_active allows only one PENDING_COUNTERPARTY row per booking.
        assertThatThrownBy(
                        () -> proposals.saveAndFlush(pending(BookingActor.GUIDE, booking.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resolvedProposal_doesNotBlockANewActiveOne() {
        RescheduleProposalEntity first = pending(BookingActor.PARTICIPANT, booking.getId());
        first.setStatus(RescheduleStatus.DECLINED);
        proposals.saveAndFlush(first);

        // A declined row is outside the partial index, so a fresh pending proposal is allowed.
        RescheduleProposalEntity second =
                proposals.saveAndFlush(pending(BookingActor.PARTICIPANT, booking.getId()));

        assertThat(second.getId()).isNotNull();
        assertThat(proposals.findByBookingIdOrderByCreatedAtDesc(booking.getId())).hasSize(2);
    }

    private static RescheduleProposalEntity pending(BookingActor by, UUID bookingId) {
        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(bookingId);
        p.setRequestedBy(by);
        p.setProposedStartAt(
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES));
        p.setProposedEndAt(Instant.now().plus(7, ChronoUnit.DAYS).plus(60, ChronoUnit.MINUTES));
        p.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        p.setFeeCents(0L);
        p.setPriceDiffCents(0L);
        p.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        return p;
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
}
