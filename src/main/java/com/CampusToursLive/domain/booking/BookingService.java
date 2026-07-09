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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Participant booking domain: dashboard reads (CTL-13), the create/cancel writes (CTL-19), and the
 * booking cart (CTL-31 — DRAFT bookings assembled item by item, submitted atomically at checkout).
 * Guide accept/decline, reschedule proposals, and payment integration are still deferred.
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

    /** Cap on free-text fields (participant notes, cancellation reason) — the columns are TEXT. */
    private static final int MAX_FREE_TEXT_LENGTH = 1000;

    /** Cap on DRAFT items per participant — keeps carts (and checkout batches) bounded. */
    private static final int MAX_CART_ITEMS = 10;

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
        BookingEntity b = buildDraftBooking(participant, req);
        requireNoHeldOverlaps(b);
        promoteToPending(b);
        try {
            // Flush now (id is assigned, so save() alone would defer the INSERT to commit):
            // the audit row's FK needs the booking row in place, and flushing here is what
            // lets this catch actually see an exclusion-constraint race.
            bookings.saveAndFlush(b);
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
        if (req != null) {
            b.setCancellationReason(cleanFreeText(req.reason(), "reason"));
        }
        bookings.save(b);
        recordTransition(b, previous, participant.getId(), "PARTICIPANT_CANCELLED");
        return toDetailResponse(b);
    }

    // ---------------------------------------------------------------------------
    // Cart (CTL-31)
    // ---------------------------------------------------------------------------

    /**
     * Add an item to the cart. Runs the full booking validation but saves the row as a DRAFT
     * booking — DRAFT is outside the DB exclusion constraints, so a carted item does NOT hold its
     * slot; the slot is only claimed at {@link #checkout}. Conflicts with held bookings and other
     * cart items are still rejected here for early feedback.
     */
    @Transactional
    public BookingDetailResponse addCartItem(UserEntity participant, CreateBookingRequest req) {
        if (bookings.countByParticipantUserIdAndStatus(participant.getId(), BookingStatus.DRAFT)
                >= MAX_CART_ITEMS) {
            throw new ValidationException("Your cart is full (max " + MAX_CART_ITEMS + " items)");
        }
        BookingEntity b = buildDraftBooking(participant, req);
        requireNoHeldOverlaps(b);
        requireNoCartOverlaps(b, cartItems(participant.getId()));
        // Flush so the audit row's FK sees the booking row (assigned id → deferred insert).
        bookings.saveAndFlush(b);
        recordTransition(b, null, participant.getId(), "CART_ITEM_ADDED");
        return toDetailResponse(b);
    }

    /** The participant's cart: their DRAFT bookings, oldest first. */
    @Transactional(readOnly = true)
    public List<BookingDetailResponse> getCart(UUID participantUserId) {
        return cartItems(participantUserId).stream().map(this::toDetailResponse).toList();
    }

    /**
     * Remove one cart item (hard delete — a DRAFT was never a commitment; its audit rows go with it
     * via ON DELETE CASCADE). Returns the remaining cart.
     */
    @Transactional
    public List<BookingDetailResponse> removeCartItem(UserEntity participant, UUID itemId) {
        BookingEntity b =
                bookings.findByIdAndParticipantUserIdAndStatus(
                                itemId, participant.getId(), BookingStatus.DRAFT)
                        .orElseThrow(() -> new NotFoundException("Cart item not found"));
        bookings.delete(b);
        return getCart(participant.getId());
    }

    /**
     * Submit the whole cart atomically. Every DRAFT is re-validated (offerings can go inactive and
     * items can go stale while carted) and flipped to PENDING_GUIDE_ACCEPTANCE inside this one
     * transaction; the DB exclusion constraints check the batch on flush, so if any slot was taken
     * concurrently the whole checkout rolls back — all or nothing.
     */
    @Transactional
    public List<BookingDetailResponse> checkout(UserEntity participant) {
        List<BookingEntity> items = cartItems(participant.getId());
        if (items.isEmpty()) {
            throw new ValidationException("Your cart is empty");
        }
        Instant now = Instant.now();
        // Re-validate (and re-snapshot) every item first, THEN cross-check overlaps: the
        // re-snapshot can change an item's duration, which changes what overlaps what.
        for (BookingEntity b : items) {
            revalidateCartItem(b, now);
        }
        for (BookingEntity b : items) {
            requireNoHeldOverlaps(b);
            requireNoCartOverlaps(b, items);
        }
        for (BookingEntity b : items) {
            promoteToPending(b);
        }
        try {
            bookings.saveAllAndFlush(items);
        } catch (DataIntegrityViolationException raceLost) {
            throw new ValidationException(
                    "One or more time slots were just taken — please review your cart");
        }
        for (BookingEntity b : items) {
            recordTransition(b, BookingStatus.DRAFT, participant.getId(), "CART_CHECKOUT");
        }
        return items.stream().map(this::toDetailResponse).toList();
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Validates a booking request end-to-end (bookable offering, approved guide, active university,
     * not the guide's own tour, time window) and builds the unsaved booking in DRAFT status with
     * the price snapshot. No slot is checked or claimed here.
     */
    private BookingEntity buildDraftBooking(UserEntity participant, CreateBookingRequest req) {
        TourOfferingEntity offering = requireBookableOffering(req.tourOfferingId());
        GuideProfileEntity guide = requireApprovedGuide(offering.getGuideId());
        requireActiveUniversity(offering.getUniversityId());
        if (guide.getUserId().equals(participant.getId())) {
            throw new ValidationException("You cannot book your own tour");
        }

        Instant start = parseStart(req.scheduledStartAt(), Instant.now());
        Instant end = start.plus(Duration.ofMinutes(offering.getDurationMin()));

        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber(generateBookingNumber());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(offering.getGuideId());
        b.setTourOfferingId(offering.getId());
        b.setUniversityId(offering.getUniversityId());
        b.setStatus(BookingStatus.DRAFT);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(end);
        b.setReservedStartAt(start);
        b.setReservedEndAt(end.plus(RESERVED_BUFFER_AFTER));
        // Price snapshot — no payments yet, so fees and taxes are zero and the guide
        // amount equals the total (see class javadoc).
        b.setBasePriceCents(offering.getPriceCents());
        b.setServiceFeeCents(0L);
        b.setTaxCents(0L);
        b.setTotalCents(offering.getPriceCents());
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(offering.getPriceCents());
        b.setCurrency(offering.getCurrency());
        b.setParticipantNotes(cleanFreeText(req.participantNotes(), "participantNotes"));
        return b;
    }

    /** DRAFT → PENDING_GUIDE_ACCEPTANCE: the transition that claims the slot. */
    private static void promoteToPending(BookingEntity b) {
        b.setStatus(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        b.setGuideResponseDeadlineAt(Instant.now().plus(GUIDE_RESPONSE_WINDOW));
    }

    /** Friendly 422s for conflicts with slot-holding bookings (DB constraints are the backstop). */
    private void requireNoHeldOverlaps(BookingEntity b) {
        if (bookings
                .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                        b.getGuideId(),
                        SLOT_HOLDING_STATUSES,
                        b.getReservedEndAt(),
                        b.getReservedStartAt())) {
            throw new ValidationException("The guide already has a booking at that time");
        }
        if (bookings
                .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                        b.getParticipantUserId(),
                        SLOT_HOLDING_STATUSES,
                        b.getScheduledEndAt(),
                        b.getScheduledStartAt())) {
            throw new ValidationException("You already have a booking that overlaps this time");
        }
    }

    /**
     * In-memory overlap check against other cart items: a participant can't be in two tours at once
     * (scheduled intervals), and two items with the same guide must respect the reserved buffer.
     * DRAFTs don't hold DB slots, so the exclusion constraints can't do this for us until checkout.
     */
    private static void requireNoCartOverlaps(BookingEntity b, List<BookingEntity> cart) {
        for (BookingEntity other : cart) {
            if (other.getId().equals(b.getId())) {
                continue;
            }
            boolean participantClash =
                    other.getScheduledStartAt().isBefore(b.getScheduledEndAt())
                            && other.getScheduledEndAt().isAfter(b.getScheduledStartAt());
            boolean guideClash =
                    other.getGuideId().equals(b.getGuideId())
                            && other.getReservedStartAt().isBefore(b.getReservedEndAt())
                            && other.getReservedEndAt().isAfter(b.getReservedStartAt());
            if (participantClash || guideClash) {
                throw new ValidationException(
                        "This time overlaps another item in your cart ("
                                + other.getBookingNumber()
                                + ")");
            }
        }
    }

    /**
     * Checkout-time re-validation: the offering/guide/university may have changed while the item
     * sat in the cart, and the start may have drifted inside the minimum-notice window. (The
     * max-advance bound can only become MORE satisfied over time, so it is not rechecked.)
     *
     * <p>Also RE-SNAPSHOTS price, currency, and duration from the offering as it stands now — a
     * DRAFT is not a commitment, so the participant commits to the current terms at checkout, not
     * the ones from add-to-cart time.
     */
    private void revalidateCartItem(BookingEntity b, Instant now) {
        TourOfferingEntity offering =
                offerings
                        .findById(b.getTourOfferingId())
                        .filter(o -> o.getStatus() == TourStatus.ACTIVE)
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "Cart item "
                                                        + b.getBookingNumber()
                                                        + " is no longer bookable"));
        try {
            requireApprovedGuide(offering.getGuideId());
            requireActiveUniversity(offering.getUniversityId());
        } catch (ValidationException notBookable) {
            throw new ValidationException(
                    "Cart item " + b.getBookingNumber() + " is no longer bookable");
        }
        if (b.getScheduledStartAt().isBefore(now.plus(MIN_NOTICE))) {
            throw new ValidationException(
                    "Cart item "
                            + b.getBookingNumber()
                            + " starts too soon — bookings need at least "
                            + MIN_NOTICE.toHours()
                            + " hours notice");
        }

        // Commitment-time snapshot (fees stay zero until payments are built).
        b.setBasePriceCents(offering.getPriceCents());
        b.setTotalCents(offering.getPriceCents());
        b.setGuideAmountCents(offering.getPriceCents());
        b.setCurrency(offering.getCurrency());
        Instant end = b.getScheduledStartAt().plus(Duration.ofMinutes(offering.getDurationMin()));
        b.setScheduledEndAt(end);
        b.setReservedEndAt(end.plus(RESERVED_BUFFER_AFTER));
    }

    private List<BookingEntity> cartItems(UUID participantUserId) {
        return bookings.findByParticipantUserIdAndStatusOrderByCreatedAtAsc(
                participantUserId, BookingStatus.DRAFT);
    }

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

    /** Trim + length-cap optional free text; blank collapses to null. */
    private static String cleanFreeText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_FREE_TEXT_LENGTH) {
            throw new ValidationException(
                    field + " must be at most " + MAX_FREE_TEXT_LENGTH + " characters");
        }
        return trimmed;
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
