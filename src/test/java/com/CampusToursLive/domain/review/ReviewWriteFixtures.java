package com.CampusToursLive.domain.review;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Seeds a committed PUBLISHED review (participant, verified guide, active offering, completed
 * booking) for the review concurrent-write integration tests. Each repository save commits on its
 * own when the calling test runs with {@code NOT_SUPPORTED} propagation.
 */
@Component
class ReviewWriteFixtures {

    private final UniversityRepository universities;
    private final UserRepository users;
    private final GuideProfileRepository guides;
    private final TourOfferingRepository offerings;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;

    ReviewWriteFixtures(
            UniversityRepository universities,
            UserRepository users,
            GuideProfileRepository guides,
            TourOfferingRepository offerings,
            BookingRepository bookings,
            ReviewRepository reviews) {
        this.universities = universities;
        this.users = users;
        this.guides = guides;
        this.offerings = offerings;
        this.bookings = bookings;
        this.reviews = reviews;
    }

    /** Persists the graph and returns the new PUBLISHED review's id. */
    UUID seedPublishedReview() {
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
        g.setStatus(GuideStatus.VERIFIED);
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

        BookingEntity booking = bookings.saveAndFlush(completedBooking(participant, offering));

        ReviewEntity r = new ReviewEntity();
        r.setId(UUID.randomUUID());
        r.setBookingId(booking.getId());
        r.setParticipantUserId(participant.getId());
        r.setGuideId(guide.getId());
        r.setTourOfferingId(offering.getId());
        r.setOverallRating((short) 4);
        r.setStatus(ReviewStatus.PUBLISHED);
        r.setPublishedAt(Instant.now());
        return reviews.saveAndFlush(r).getId();
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

    private static BookingEntity completedBooking(
            UserEntity participant, TourOfferingEntity offering) {
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-IT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(offering.getGuideId());
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
