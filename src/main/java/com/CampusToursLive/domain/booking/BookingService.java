package com.CampusToursLive.domain.booking;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p><b>MVP booking policy.</b> Acceptance is always MANUAL (new bookings wait for the guide, with
 * a 90-minute response window — not yet guide-configurable). Minimum notice, maximum advance, and
 * the reserved-interval buffers ARE guide-configurable via {@code guide_booking_settings} (CTL-54
 * Task 5's read/write endpoints) and are read here — {@code createBooking}/{@code addCartItem} (via
 * {@link #buildDraftBooking}) and {@code checkout} (via {@link #revalidateCartItem}) both load the
 * guide's row, falling back to the table's schema defaults (24h notice, 30-day advance, 0-minute
 * pre-tour buffer, 15-minute post-tour buffer) when a guide has none yet — the SAME fallback {@link
 * SlotGenerationService} uses, so the two paths never disagree about what is bookable. Payments are
 * not built yet, so the PENDING_PAYMENT_AUTH stage is skipped and the platform fee snapshot is
 * zero.
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

    /**
     * Statuses that hold a slot — mirrors the WHERE clause of the DB exclusion constraints.
     * Package-private (not {@code private}) so {@link SlotGenerationService} can subtract the SAME
     * set of held bookings from candidate slots — a CONFIRMED-only view (like {@link
     * BookingRepository
     * #findByGuideIdAndStatusAndScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc}, used
     * by Task 7) would under-count what actually occupies a guide's calendar.
     */
    static final List<BookingStatus> SLOT_HOLDING_STATUSES =
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

    // MVP policy: acceptance mode + response window are not yet guide-configurable. Notice,
    // advance, and buffers ARE — see loadSettings() and the class javadoc.
    private static final Duration GUIDE_RESPONSE_WINDOW = Duration.ofMinutes(90);

    /** Cap on free-text fields (participant notes, cancellation reason) — the columns are TEXT. */
    private static final int MAX_FREE_TEXT_LENGTH = 1000;

    /** Cap on DRAFT items per participant — keeps carts (and checkout batches) bounded. */
    private static final int MAX_CART_ITEMS = 10;

    private static final String BOOKING_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BOOKING_NUMBER_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * CTL-54 Task 6: "outside the guide's availability" — distinct from the slot-conflict messages
     * ("already has a booking" / "overlaps another item"), which are booking-vs-booking, not
     * booking-vs-availability.
     */
    static final String OUTSIDE_AVAILABILITY_MESSAGE =
            "This time is outside the guide's availability";

    private final BookingRepository bookings;
    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final UserRepository users;
    private final UniversityRepository universities;
    private final BookingStatusHistoryRepository statusHistory;
    private final GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    private final GuideBookingSettingsRepository settings;

    public BookingService(
            BookingRepository bookings,
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            UserRepository users,
            UniversityRepository universities,
            BookingStatusHistoryRepository statusHistory,
            GuideAvailabilityOccurrenceRepository availabilityOccurrences,
            GuideBookingSettingsRepository settings) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.guides = guides;
        this.users = users;
        this.universities = universities;
        this.statusHistory = statusHistory;
        this.availabilityOccurrences = availabilityOccurrences;
        this.settings = settings;
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
        return toDetailResponses(
                bookings
                        .findByParticipantUserIdAndStatusInAndScheduledStartAtAfterOrderByScheduledStartAtAsc(
                                participantUserId, UPCOMING_STATUSES, Instant.now()));
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
     * Create a booking for a bookable offering (ACTIVE offering, VERIFIED guide, ACTIVE
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
        return toDetailResponses(cartItems(participantUserId));
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
        return toDetailResponses(items);
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

        Instant start = parseStart(req.scheduledStartAt());
        GuideBookingSettingsEntity guideSettings = loadSettings(offering.getGuideId());
        requireWithinNoticeAndAdvance(start, Instant.now(), guideSettings);
        Instant end = start.plus(Duration.ofMinutes(offering.getDurationMin()));
        requireWithinAvailability(offering.getGuideId(), start, end);

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
        b.setReservedStartAt(start.minus(Duration.ofMinutes(guideSettings.getBufferBeforeMin())));
        b.setReservedEndAt(end.plus(Duration.ofMinutes(guideSettings.getBufferAfterMin())));
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

    /**
     * CTL-54 Task 6: the booking's SCHEDULED tour interval (never the buffer-padded reserved
     * interval) must be CONTAINED by some current materialized availability occurrence for the
     * guide — {@code occurrence.during @> booking.scheduled}. This is a containment check, never an
     * EXCLUDE (that relationship is reserved for availability-vs-availability and
     * booking-vs-booking overlap, which are separate invariants). Called from the shared {@link
     * #buildDraftBooking} (covers both {@link #createBooking} and {@link #addCartItem}) and again
     * from {@link #revalidateCartItem} at checkout, since availability can change while an item
     * sits in the cart.
     */
    private void requireWithinAvailability(
            UUID guideId, Instant scheduledStart, Instant scheduledEnd) {
        if (!availabilityOccurrences.existsContaining(guideId, scheduledStart, scheduledEnd)) {
            throw new ValidationException(OUTSIDE_AVAILABILITY_MESSAGE);
        }
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
     * sat in the cart, and the start may have drifted inside the minimum-notice window — or the
     * guide may since have TIGHTENED their notice/advance/buffer settings (these are
     * guide-configurable, not fixed constants, so unlike a fixed window neither bound is guaranteed
     * to only become more satisfied over time). Both bounds are therefore rechecked here against
     * the guide's CURRENT settings, same as {@link #buildDraftBooking}.
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

        GuideBookingSettingsEntity guideSettings = loadSettings(b.getGuideId());
        Duration minNotice = Duration.ofMinutes(guideSettings.getMinNoticeMin());
        if (b.getScheduledStartAt().isBefore(now.plus(minNotice))) {
            throw new ValidationException(
                    "Cart item "
                            + b.getBookingNumber()
                            + " starts too soon — bookings need at least "
                            + formatNoticeWindow(minNotice)
                            + " notice");
        }
        Duration maxAdvance = Duration.ofDays(guideSettings.getMaxAdvanceDays());
        if (b.getScheduledStartAt().isAfter(now.plus(maxAdvance))) {
            throw new ValidationException(
                    "Cart item "
                            + b.getBookingNumber()
                            + " is too far out — bookings can be made at most "
                            + maxAdvance.toDays()
                            + " days in advance");
        }

        // Commitment-time snapshot (fees stay zero until payments are built).
        b.setBasePriceCents(offering.getPriceCents());
        b.setTotalCents(offering.getPriceCents());
        b.setGuideAmountCents(offering.getPriceCents());
        b.setCurrency(offering.getCurrency());
        Instant end = b.getScheduledStartAt().plus(Duration.ofMinutes(offering.getDurationMin()));
        b.setScheduledEndAt(end);
        b.setReservedStartAt(
                b.getScheduledStartAt()
                        .minus(Duration.ofMinutes(guideSettings.getBufferBeforeMin())));
        b.setReservedEndAt(end.plus(Duration.ofMinutes(guideSettings.getBufferAfterMin())));

        // CTL-54 Task 6: re-check containment at checkout — the guide's availability can change
        // (or the re-snapshot above can change the scheduled end) between add-to-cart and
        // checkout, so an item that was contained when carted must still be contained now.
        if (!availabilityOccurrences.existsContaining(
                b.getGuideId(), b.getScheduledStartAt(), end)) {
            throw new ValidationException(
                    "Cart item "
                            + b.getBookingNumber()
                            + " is no longer within the guide's availability");
        }
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
                .filter(g -> g.getStatus() == GuideStatus.VERIFIED)
                .orElseThrow(() -> new ValidationException("This tour is not currently bookable"));
    }

    private void requireActiveUniversity(UUID universityId) {
        universities
                .findById(universityId)
                .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                .orElseThrow(() -> new ValidationException("This tour is not currently bookable"));
    }

    /**
     * Format-parsing ONLY — the notice/advance window check used to live here too, but that check
     * needs the guide's settings (not available until the offering/guide are resolved in {@link
     * #buildDraftBooking}), so it now lives in {@link #requireWithinNoticeAndAdvance}.
     */
    private static Instant parseStart(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("scheduledStartAt is required");
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ex) {
            throw new ValidationException(
                    "scheduledStartAt must be an ISO-8601 instant, e.g. 2026-07-10T17:00:00Z");
        }
    }

    /**
     * The guide's booking-policy row, or the {@code guide_booking_settings} schema defaults (see
     * {@link GuideBookingSettingsEntity}'s javadoc) when the guide has none yet — EXACTLY the
     * fallback {@link SlotGenerationService} already uses, so a slot it lists as free is bookable
     * here under the SAME notice/advance/buffer rules.
     */
    private GuideBookingSettingsEntity loadSettings(UUID guideId) {
        return settings.findByGuideId(guideId).orElseGet(GuideBookingSettingsEntity::new);
    }

    /**
     * Enforces the guide's minimum-notice / maximum-advance window on a candidate start, using
     * their OWN {@code guide_booking_settings} (see {@link #loadSettings}) rather than a hardcoded
     * constant. Shared by {@link #buildDraftBooking} (create + cart-add); {@link
     * #revalidateCartItem} (checkout) re-runs the same two comparisons with its own
     * cart-item-prefixed messages, since the guide's settings can tighten while an item sits in the
     * cart.
     */
    private static void requireWithinNoticeAndAdvance(
            Instant start, Instant now, GuideBookingSettingsEntity guideSettings) {
        Duration minNotice = Duration.ofMinutes(guideSettings.getMinNoticeMin());
        if (start.isBefore(now.plus(minNotice))) {
            throw new ValidationException(
                    "Bookings need at least " + formatNoticeWindow(minNotice) + " notice");
        }
        Duration maxAdvance = Duration.ofDays(guideSettings.getMaxAdvanceDays());
        if (start.isAfter(now.plus(maxAdvance))) {
            throw new ValidationException(
                    "Bookings can be made at most " + maxAdvance.toDays() + " days in advance");
        }
    }

    /**
     * Renders a guide-configurable minimum-notice window for error messages. {@code
     * Duration.toHours()} TRUNCATES non-hour-aligned values (e.g. 90 minutes -> "1 hours", which is
     * both wrong and ungrammatical), and {@code minNoticeMin} is guide-configurable so it is no
     * longer guaranteed to be an exact multiple of 60. Whole-hour values (including the
     * schema-default 1440min) still read as "{n} hour(s)"; anything else reads as "{n} minutes".
     */
    private static String formatNoticeWindow(Duration minNotice) {
        long minutes = minNotice.toMinutes();
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + " minutes";
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

    /**
     * Maps a LIST of bookings to responses with the name lookups batched (CTL-35): each of the four
     * related entities (offering, guide profile, that guide's user, university) is fetched once for
     * the whole list via {@code findAllById} and joined in memory, instead of the per-row {@code
     * findById} fan-out {@link #toDetailResponse(BookingEntity)} does (~4 queries/row → N+1). Used
     * by every list surface — {@code getUpcomingBookings}, {@code getCart}, {@code checkout} — and
     * the place to hook the future {@code GET /bookings} list (CTL-39) in.
     */
    private List<BookingDetailResponse> toDetailResponses(List<BookingEntity> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, TourOfferingEntity> offeringsById =
                byId(
                        offerings.findAllById(distinctIds(list, BookingEntity::getTourOfferingId)),
                        TourOfferingEntity::getId);
        Map<UUID, GuideProfileEntity> guidesById =
                byId(
                        guides.findAllById(distinctIds(list, BookingEntity::getGuideId)),
                        GuideProfileEntity::getId);
        Map<UUID, UserEntity> guideUsersById =
                byId(
                        users.findAllById(
                                guidesById.values().stream()
                                        .map(GuideProfileEntity::getUserId)
                                        .distinct()
                                        .toList()),
                        UserEntity::getId);
        Map<UUID, UniversityEntity> universitiesById =
                byId(
                        universities.findAllById(distinctIds(list, BookingEntity::getUniversityId)),
                        UniversityEntity::getId);

        return list.stream()
                .map(
                        b ->
                                toDetailResponse(
                                        b,
                                        offeringTitleOf(offeringsById.get(b.getTourOfferingId())),
                                        guideNameOf(guidesById.get(b.getGuideId()), guideUsersById),
                                        universityNameOf(
                                                universitiesById.get(b.getUniversityId()))))
                .toList();
    }

    private BookingDetailResponse toDetailResponse(BookingEntity b) {
        return toDetailResponse(
                b,
                resolveOfferingTitle(b.getTourOfferingId()),
                resolveGuideName(b.getGuideId()),
                resolveUniversityName(b.getUniversityId()));
    }

    /** Builds the wire response once the three related names are resolved (batched or per-row). */
    private static BookingDetailResponse toDetailResponse(
            BookingEntity b, String offeringTitle, String guideName, String universityName) {
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

    private static List<UUID> distinctIds(
            List<BookingEntity> list, Function<BookingEntity, UUID> idOf) {
        return list.stream().map(idOf).distinct().toList();
    }

    private static <T> Map<UUID, T> byId(List<T> entities, Function<T, UUID> idOf) {
        return entities.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }

    private static String offeringTitleOf(TourOfferingEntity o) {
        return o != null ? o.getTitle() : "Tour";
    }

    private static String guideNameOf(GuideProfileEntity guide, Map<UUID, UserEntity> usersById) {
        if (guide == null) {
            return "";
        }
        UserEntity u = usersById.get(guide.getUserId());
        return u != null ? u.getDisplayName() : "";
    }

    private static String universityNameOf(UniversityEntity u) {
        return u != null ? u.getName() : "";
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
