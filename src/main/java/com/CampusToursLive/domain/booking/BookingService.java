package com.CampusToursLive.domain.booking;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.PendingActionsResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Participant booking domain: dashboard reads (CTL-13) and the create/cancel writes (CTL-19). Guide
 * accept/decline, reschedule proposals, and payment integration are still deferred.
 *
 * <p>The {@code guideId} on both {@code BookingEntity} and {@code TourOfferingEntity} is the {@code
 * guide_profiles.id} primary key, not the user id — resolving to a display name requires a two-step
 * lookup through {@code GuideProfileRepository}.
 *
 * <p><b>MVP booking policy.</b> Until {@code guide_booking_settings} gets endpoints, every create
 * uses that table's schema defaults: MANUAL acceptance (new bookings wait for the guide, with a
 * 90-minute response window), 24h minimum notice, 30-day maximum advance, and a 15-minute post-tour
 * buffer on the guide's reserved interval. Payments are not built yet, so the PENDING_PAYMENT_AUTH
 * stage is skipped and the platform fee snapshot is zero.
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

    /** Statuses that hold a slot — mirrors the WHERE clause of the DB exclusion constraints. */
    private static final List<BookingStatus> SLOT_HOLDING_STATUSES =
            List.of(
                    BookingStatus.PENDING_PAYMENT_AUTH,
                    BookingStatus.PENDING_GUIDE_ACCEPTANCE,
                    BookingStatus.PAYMENT_ACTION_REQUIRED,
                    BookingStatus.CONFIRMED,
                    BookingStatus.IN_PROGRESS);

    /** Statuses a participant may cancel from (before the tour starts). */
    private static final List<BookingStatus> PARTICIPANT_CANCELLABLE_STATUSES =
            List.of(
                    BookingStatus.PENDING_PAYMENT_AUTH,
                    BookingStatus.PENDING_GUIDE_ACCEPTANCE,
                    BookingStatus.PAYMENT_ACTION_REQUIRED,
                    BookingStatus.CONFIRMED);

    // MVP policy constants — the guide_booking_settings schema defaults (see class javadoc).
    private static final Duration MIN_NOTICE = Duration.ofHours(24);
    private static final Duration MAX_ADVANCE = Duration.ofDays(30);
    private static final Duration GUIDE_RESPONSE_WINDOW = Duration.ofMinutes(90);
    private static final Duration RESERVED_BUFFER_AFTER = Duration.ofMinutes(15);

    private static final String BOOKING_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BOOKING_NUMBER_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final UserRepository users;
    private final UniversityRepository universities;
    private final BookingStatusHistoryRepository statusHistory;

    public BookingService(
            BookingRepository bookings,
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            UserRepository users,
            UniversityRepository universities,
            BookingStatusHistoryRepository statusHistory) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.guides = guides;
        this.users = users;
        this.universities = universities;
        this.statusHistory = statusHistory;
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
    // Writes (CTL-19)
    // ---------------------------------------------------------------------------

    /**
     * Create a booking for a bookable offering (ACTIVE offering, APPROVED guide, ACTIVE
     * university). The offering's duration and price are snapshotted onto the booking; the booking
     * starts in PENDING_GUIDE_ACCEPTANCE with a 90-minute guide response window (MANUAL acceptance
     * — see the class javadoc for the MVP policy). Overlaps are pre-checked here and enforced
     * transactionally by the DB exclusion constraints.
     */
    @Transactional
    public BookingDetailResponse createBooking(UserEntity participant, CreateBookingRequest req) {
        TourOfferingEntity offering = requireBookableOffering(req.tourOfferingId());
        GuideProfileEntity guide = requireApprovedGuide(offering.getGuideId());
        requireActiveUniversity(offering.getUniversityId());
        if (guide.getUserId().equals(participant.getId())) {
            throw new ValidationException("You cannot book your own tour");
        }

        Instant now = Instant.now();
        Instant start = parseStart(req.scheduledStartAt(), now);
        Instant end = start.plus(Duration.ofMinutes(offering.getDurationMin()));
        Instant reservedEnd = end.plus(RESERVED_BUFFER_AFTER);
        String timezone = parseTimezone(req.displayTimezone());

        if (bookings
                .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                        offering.getGuideId(), SLOT_HOLDING_STATUSES, reservedEnd, start)) {
            throw new ValidationException("The guide is not available at that time");
        }
        if (bookings
                .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                        participant.getId(), SLOT_HOLDING_STATUSES, end, start)) {
            throw new ValidationException("You already have a booking that overlaps this time");
        }

        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber(generateBookingNumber());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(offering.getGuideId());
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(end);
        b.setDisplayTimezone(timezone);
        b.setReservedStartAt(start);
        b.setReservedEndAt(reservedEnd);
        b.setGuideResponseDeadlineAt(now.plus(GUIDE_RESPONSE_WINDOW));
        // Price snapshot — no payments yet, so fees and taxes are zero and the guide
        // amount equals the total (see class javadoc).
        b.setBasePriceCents(offering.getPriceCents());
        b.setServiceFeeCents(0L);
        b.setTaxCents(0L);
        b.setTotalCents(offering.getPriceCents());
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(offering.getPriceCents());
        b.setCurrency(offering.getCurrency());
        if (req.participantNotes() != null && !req.participantNotes().isBlank()) {
            b.setParticipantNotes(req.participantNotes().trim());
        }

        try {
            bookings.save(b);
        } catch (DataIntegrityViolationException raceLost) {
            // A concurrent request won the slot between our pre-check and the insert —
            // the exclusion constraint is the source of truth.
            throw new ValidationException(
                    "That time slot was just taken — please pick another time");
        }
        recordTransition(b, null, participant.getId(), "PARTICIPANT_CREATED");
        return toDetailResponse(b);
    }

    /**
     * Cancel the participant's own upcoming booking. Allowed from any pre-tour active status
     * (pending payment/acceptance or CONFIRMED) until the scheduled start; idempotent if the
     * booking is already participant-cancelled.
     */
    @Transactional
    public BookingDetailResponse cancelBooking(
            UserEntity participant, UUID bookingId, CancelBookingRequest req) {
        BookingEntity b =
                bookings.findByIdAndParticipantUserId(bookingId, participant.getId())
                        .orElseThrow(() -> new NotFoundException("Booking not found"));

        if (b.getStatus() == BookingStatus.CANCELLED_BY_PARTICIPANT) {
            return toDetailResponse(b); // idempotent
        }
        if (!PARTICIPANT_CANCELLABLE_STATUSES.contains(b.getStatus())) {
            throw new ValidationException("This booking can no longer be cancelled");
        }
        if (!b.getScheduledStartAt().isAfter(Instant.now())) {
            throw new ValidationException(
                    "This booking has already started and can no longer be cancelled");
        }

        BookingStatus previous = b.getStatus();
        b.setStatus(BookingStatus.CANCELLED_BY_PARTICIPANT);
        b.setCancellationActor(BookingActor.PARTICIPANT);
        b.setCancelledAt(Instant.now());
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            b.setCancellationReason(req.reason().trim());
        }
        bookings.save(b);
        recordTransition(b, previous, participant.getId(), "PARTICIPANT_CANCELLED");
        return toDetailResponse(b);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /** The offering must exist and be ACTIVE — anything else is invisible to participants (404). */
    private TourOfferingEntity requireBookableOffering(String rawOfferingId) {
        if (rawOfferingId == null || rawOfferingId.isBlank()) {
            throw new ValidationException("tourOfferingId is required");
        }
        UUID offeringId;
        try {
            offeringId = UUID.fromString(rawOfferingId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid tourOfferingId: " + rawOfferingId);
        }
        return offerings
                .findById(offeringId)
                .filter(o -> o.getStatus() == TourStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Tour not found"));
    }

    private GuideProfileEntity requireApprovedGuide(UUID guideProfileId) {
        return guides.findById(guideProfileId)
                .filter(g -> g.getApplicationStatus() == GuideApplicationStatus.APPROVED)
                .orElseThrow(() -> new ValidationException("This tour is not currently bookable"));
    }

    private void requireActiveUniversity(UUID universityId) {
        universities
                .findById(universityId)
                .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                .orElseThrow(() -> new ValidationException("This tour is not currently bookable"));
    }

    private static Instant parseStart(String raw, Instant now) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("scheduledStartAt is required");
        }
        Instant start;
        try {
            start = Instant.parse(raw.trim());
        } catch (Exception ex) {
            throw new ValidationException(
                    "scheduledStartAt must be an ISO-8601 instant, e.g. 2026-07-10T17:00:00Z");
        }
        if (start.isBefore(now.plus(MIN_NOTICE))) {
            throw new ValidationException(
                    "Bookings need at least " + MIN_NOTICE.toHours() + " hours notice");
        }
        if (start.isAfter(now.plus(MAX_ADVANCE))) {
            throw new ValidationException(
                    "Bookings can be made at most " + MAX_ADVANCE.toDays() + " days in advance");
        }
        return start;
    }

    private static String parseTimezone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("displayTimezone is required");
        }
        try {
            return ZoneId.of(raw.trim()).getId();
        } catch (Exception ex) {
            throw new ValidationException("Invalid displayTimezone: " + raw);
        }
    }

    /**
     * Human-facing unique reference, e.g. {@code BK-7F3K2M9QX1}. The 32-char alphabet omits the
     * look-alikes 0/O and 1/I; 10 chars ≈ 10^15 combinations, so collisions are practically
     * impossible (the DB unique constraint is the backstop).
     */
    private static String generateBookingNumber() {
        StringBuilder sb = new StringBuilder("BK-");
        for (int i = 0; i < BOOKING_NUMBER_LENGTH; i++) {
            sb.append(
                    BOOKING_NUMBER_ALPHABET.charAt(
                            RANDOM.nextInt(BOOKING_NUMBER_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Append one row to the booking_status_history audit trail. */
    private void recordTransition(
            BookingEntity b, BookingStatus previous, UUID actorUserId, String reasonCode) {
        BookingStatusHistoryEntity h = new BookingStatusHistoryEntity();
        h.setBookingId(b.getId());
        h.setPreviousStatus(previous);
        h.setNewStatus(b.getStatus());
        h.setActorType(BookingActor.PARTICIPANT);
        h.setActorUserId(actorUserId);
        h.setReasonCode(reasonCode);
        statusHistory.save(h);
    }

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
