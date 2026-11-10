package com.CampusToursLive.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.CampusToursLive.web.dto.PublicReviewResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Public review read surfaces against a REAL PostgreSQL (Testcontainers): PUBLISHED-only filtering,
 * newest-first ordering by published_at, batch-resolved reviewer names, and pagination. Requires a
 * running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ReviewService.class)
class ReviewReadIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired BookingRepository bookings;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UserRepository users;
    @Autowired UniversityRepository universities;

    private GuideProfileEntity guide;
    private TourOfferingEntity offering;

    @BeforeEach
    void seedGraph() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();

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
    void getGuideReviews_returnsPublishedNewestFirst_withNames_excludingNonPublished() {
        Instant now = Instant.now();
        seedReview("Alice", 5, ReviewStatus.PUBLISHED, now.minus(2, ChronoUnit.HOURS));
        seedReview("Bob", 4, ReviewStatus.PUBLISHED, now.minus(1, ChronoUnit.HOURS));
        seedReview("Carol", 1, ReviewStatus.REMOVED, now); // must be excluded

        Page<PublicReviewResponse> page = service.getGuideReviews(guide.getId(), 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        // Newest first: Bob (-1h) before Alice (-2h); Carol (REMOVED) absent.
        assertThat(page.getContent())
                .extracting(PublicReviewResponse::reviewerName)
                .containsExactly("Bob", "Alice");
        assertThat(page.getContent().get(0).overallRating()).isEqualTo(4);
        assertThat(page.getContent().get(0).guideId()).isEqualTo(guide.getId().toString());
    }

    @Test
    void getOfferingReviews_returnsPublishedForOffering() {
        Instant now = Instant.now();
        seedReview("Dan", 5, ReviewStatus.PUBLISHED, now);

        Page<PublicReviewResponse> page = service.getOfferingReviews(offering.getId(), 0, 20);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).offeringId()).isEqualTo(offering.getId().toString());
        assertThat(page.getContent().get(0).reviewerName()).isEqualTo("Dan");
    }

    /**
     * Insert a PUBLISHED/REMOVED review by {@code reviewerName} against the seeded guide/offering.
     */
    private void seedReview(
            String reviewerName, int overall, ReviewStatus status, Instant publishedAt) {
        UserEntity reviewer = users.save(user(reviewerName));
        BookingEntity booking = bookings.saveAndFlush(completedBooking(reviewer.getId()));

        ReviewEntity r = new ReviewEntity();
        r.setId(UUID.randomUUID());
        r.setBookingId(booking.getId());
        r.setParticipantUserId(reviewer.getId());
        r.setGuideId(guide.getId());
        r.setTourOfferingId(offering.getId());
        r.setOverallRating((short) overall);
        r.setComment("comment by " + reviewerName);
        r.setPrivateFeedback("private — must never leak");
        r.setStatus(status);
        r.setPublishedAt(publishedAt);
        reviews.saveAndFlush(r);
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

    private BookingEntity completedBooking(UUID participantUserId) {
        Instant start = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participantUserId);
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
