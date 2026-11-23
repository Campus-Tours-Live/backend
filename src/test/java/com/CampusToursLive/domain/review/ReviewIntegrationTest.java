package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
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
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateReviewRequest;
import com.CampusToursLive.web.dto.ModerateReviewRequest;
import com.CampusToursLive.web.dto.ReviewResponse;
import java.math.BigDecimal;
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
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reviews integration test against a REAL PostgreSQL (Testcontainers). Exercises what the Mockito
 * tests can't: the PG {@code review_status} enum binding on a real INSERT, the {@code
 * UNIQUE(booking_id)} constraint, and — the whole point of the feature — that publishing a review
 * recomputes {@code avg_rating}/{@code review_count} on BOTH guide_profiles and tour_offerings from
 * the source of truth. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ReviewService.class)
class ReviewIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired BookingRepository bookings;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UserRepository users;
    @Autowired UniversityRepository universities;
    @Autowired TestEntityManager em;

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
        g.setStatus(GuideStatus.VERIFIED);
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
    void createReview_persistsPublished_andRecomputesGuideAndOfferingAggregates() {
        BookingEntity booking = bookings.saveAndFlush(completedBooking(3));

        ReviewResponse resp =
                service.createReview(
                        participant,
                        booking.getId(),
                        new CreateReviewRequest(
                                4, 5, 4, 5, 3, "Solid tour", "meet-point was vague"));

        assertThat(resp.status()).isEqualTo("PUBLISHED");
        assertThat(resp.overallRating()).isEqualTo(4);

        ReviewEntity saved = reviews.findByBookingId(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getGuideId()).isEqualTo(guide.getId());
        assertThat(saved.getPrivateFeedback()).isEqualTo("meet-point was vague");

        em.flush();
        em.clear();

        TourOfferingEntity freshOffering = offerings.findById(offering.getId()).orElseThrow();
        assertThat(freshOffering.getReviewCount()).isEqualTo(1);
        assertThat(freshOffering.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(guideAvg()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(guideCount()).isEqualTo(1);
    }

    @Test
    void createReview_secondReviewSameBooking_isRejected() {
        BookingEntity booking = bookings.saveAndFlush(completedBooking(3));
        service.createReview(
                participant,
                booking.getId(),
                new CreateReviewRequest(5, null, null, null, null, null, null));

        assertThatThrownBy(
                        () ->
                                service.createReview(
                                        participant,
                                        booking.getId(),
                                        new CreateReviewRequest(
                                                3, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReview_acrossTwoBookings_aggregateIsAverageOfPublished() {
        BookingEntity first = bookings.saveAndFlush(completedBooking(3));
        BookingEntity second = bookings.saveAndFlush(completedBooking(6));

        service.createReview(
                participant,
                first.getId(),
                new CreateReviewRequest(5, null, null, null, null, null, null));
        service.createReview(
                participant,
                second.getId(),
                new CreateReviewRequest(3, null, null, null, null, null, null));

        em.flush();
        em.clear();

        TourOfferingEntity freshOffering = offerings.findById(offering.getId()).orElseThrow();
        assertThat(freshOffering.getReviewCount()).isEqualTo(2);
        assertThat(freshOffering.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(guideCount()).isEqualTo(2);
        assertThat(guideAvg()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void createReview_onNonCompletedBooking_isRejected() {
        BookingEntity confirmed = completedBooking(3);
        confirmed.setStatus(BookingStatus.CONFIRMED);
        bookings.saveAndFlush(confirmed);

        assertThatThrownBy(
                        () ->
                                service.createReview(
                                        participant,
                                        confirmed.getId(),
                                        new CreateReviewRequest(
                                                5, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThat(reviews.existsByBookingId(confirmed.getId())).isFalse();
    }

    @Test
    void getReviewForBooking_returnsPersistedReview() {
        BookingEntity booking = bookings.saveAndFlush(completedBooking(3));
        service.createReview(
                participant,
                booking.getId(),
                new CreateReviewRequest(4, null, null, null, null, "nice", "private note"));

        ReviewResponse resp = service.getReviewForBooking(participant, booking.getId());
        assertThat(resp.overallRating()).isEqualTo(4);
        assertThat(resp.comment()).isEqualTo("nice");
        assertThat(resp.privateFeedback()).isEqualTo("private note");
    }

    @Test
    void moderateReview_removePublished_dropsAggregatesToZero() {
        BookingEntity booking = bookings.saveAndFlush(completedBooking(3));
        ReviewResponse created =
                service.createReview(
                        participant,
                        booking.getId(),
                        new CreateReviewRequest(5, null, null, null, null, null, null));
        UUID reviewId = UUID.fromString(created.id());

        ReviewResponse moderated =
                service.moderateReview(reviewId, new ModerateReviewRequest("REMOVED"));
        assertThat(moderated.status()).isEqualTo("REMOVED");

        em.flush();
        em.clear();

        assertThat(reviews.findById(reviewId).orElseThrow().getStatus())
                .isEqualTo(ReviewStatus.REMOVED);
        // Only PUBLISHED reviews count — removal drops the aggregates back to zero.
        assertThat(offerings.findById(offering.getId()).orElseThrow().getReviewCount())
                .isEqualTo(0);
        assertThat(guideCount()).isEqualTo(0);
    }

    private BigDecimal guideAvg() {
        return (BigDecimal)
                em.getEntityManager()
                        .createNativeQuery("SELECT avg_rating FROM guide_profiles WHERE id = ?")
                        .setParameter(1, guide.getId())
                        .getSingleResult();
    }

    private int guideCount() {
        Number n =
                (Number)
                        em.getEntityManager()
                                .createNativeQuery(
                                        "SELECT review_count FROM guide_profiles WHERE id = ?")
                                .setParameter(1, guide.getId())
                                .getSingleResult();
        return n.intValue();
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

    /**
     * A COMPLETED booking for this test's participant/guide/offering, {@code daysAgo} in the past.
     */
    private BookingEntity completedBooking(int daysAgo) {
        Instant start =
                Instant.now().minus(daysAgo, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(guide.getId());
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(BookingStatus.COMPLETED);
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
