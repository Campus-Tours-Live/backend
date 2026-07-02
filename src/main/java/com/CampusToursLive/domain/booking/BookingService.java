package com.CampusToursLive.domain.booking;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only dashboard queries for the participant booking domain. All writes (create, cancel,
 * reschedule) are deferred until those features are built.
 *
 * <p>The {@code guideId} on both {@code BookingEntity} and {@code TourOfferingEntity} is the {@code
 * guide_profiles.id} primary key, not the user id — resolving to a display name requires a two-step
 * lookup through {@code GuideProfileRepository}.
 */
@Service
public class BookingService {

    /** Statuses considered "upcoming" for the participant dashboard list. */
    private static final List<BookingStatus> UPCOMING_STATUSES =
            List.of(
                    BookingStatus.CONFIRMED,
                    BookingStatus.PENDING_GUIDE_ACCEPTANCE,
                    BookingStatus.PENDING_PAYMENT_AUTH,
                    BookingStatus.PAYMENT_ACTION_REQUIRED);

    private static final List<BookingStatus> PAYMENT_PENDING_STATUSES =
            List.of(BookingStatus.PENDING_PAYMENT_AUTH, BookingStatus.PAYMENT_ACTION_REQUIRED);

    private final BookingRepository bookings;
    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final UserRepository users;
    private final UniversityRepository universities;

    public BookingService(
            BookingRepository bookings,
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            UserRepository users,
            UniversityRepository universities) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.guides = guides;
        this.users = users;
        this.universities = universities;
    }

    /**
     * The soonest upcoming CONFIRMED booking — shown in the "Next Tour" card. Returns empty if the
     * participant has no confirmed future bookings.
     */
    @Transactional(readOnly = true)
    public Optional<BookingDetailResponse> getNextTour(UUID participantUserId) {
        return bookings.findFirstByParticipantUserIdAndStatusAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                        participantUserId, BookingStatus.CONFIRMED, Instant.now())
                .map(this::toDetailResponse);
    }

    /**
     * All active-lifecycle bookings that have not yet started, ordered chronologically. Covers the
     * "Upcoming Tours" list — includes CONFIRMED, pending payment, and pending guide acceptance.
     */
    @Transactional(readOnly = true)
    public List<BookingDetailResponse> getUpcomingBookings(UUID participantUserId) {
        return bookings
                .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                        participantUserId, UPCOMING_STATUSES, Instant.now())
                .stream()
                .map(this::toDetailResponse)
                .toList();
    }

    /**
     * Counts that drive the "Pending Actions" card: payments to finish, bookings waiting on guide
     * acceptance, and completed tours that have not yet received a review.
     */
    @Transactional(readOnly = true)
    public PendingActionsResponse getPendingActions(UUID participantUserId) {
        long paymentsToFinish =
                bookings.countByParticipantUserIdAndStatusIn(
                        participantUserId, PAYMENT_PENDING_STATUSES);
        long waitingForGuide =
                bookings.countByParticipantUserIdAndStatusIn(
                        participantUserId, List.of(BookingStatus.PENDING_GUIDE_ACCEPTANCE));
        long reviewsToWrite = bookings.countCompletedWithoutReview(participantUserId);
        return new PendingActionsResponse(paymentsToFinish, waitingForGuide, reviewsToWrite);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private BookingDetailResponse toDetailResponse(BookingEntity b) {
        String offeringTitle = resolveOfferingTitle(b.getTourOfferingId());
        String guideName = resolveGuideName(b.getGuideId());
        String universityName = resolveUniversityName(b.getUniversityId());
        int durationMin =
                (int) Duration.between(b.getScheduledStartAt(), b.getScheduledEndAt()).toMinutes();
        String guideResponseDeadline =
                b.getGuideResponseDeadlineAt() != null
                        ? b.getGuideResponseDeadlineAt().toString()
                        : null;

        return new BookingDetailResponse(
                b.getId().toString(),
                b.getStatus().displayStatus(),
                b.getScheduledStartAt().toString(),
                b.getDisplayTimezone(),
                b.getTourOfferingId().toString(),
                offeringTitle,
                guideName,
                guideResponseDeadline,
                universityName,
                durationMin,
                b.getBasePriceCents(),
                b.getCurrency());
    }

    private String resolveOfferingTitle(UUID offeringId) {
        return offerings.findById(offeringId).map(TourOfferingEntity::getTitle).orElse("Tour");
    }

    /** guideId is guide_profiles.id → look up the profile, then the user's display name. */
    private String resolveGuideName(UUID guideProfileId) {
        return guides.findById(guideProfileId)
                .map(GuideProfileEntity::getUserId)
                .flatMap(users::findById)
                .map(UserEntity::getDisplayName)
                .orElse("");
    }

    private String resolveUniversityName(UUID universityId) {
        return universities.findById(universityId).map(UniversityEntity::getName).orElse("");
    }
}
