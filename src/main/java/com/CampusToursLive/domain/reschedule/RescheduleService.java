package com.CampusToursLive.domain.reschedule;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.booking.BookingActor;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CTL-50 propose: create a PENDING_COUNTERPARTY proposal to move a CONFIRMED booking. Owner =
 * participant or guide (else 404). Notice/advance + availability + slot conflicts (exclude self);
 * state conflicts → 409, bad input → 422. fee/priceDiff = 0; reason not persisted in MVP. Resolve
 * is CTL-51.
 */
@Service
public class RescheduleService {

    /** Counterparty response window, capped at the booking's current start. */
    static final Duration COUNTERPARTY_RESPONSE_WINDOW = Duration.ofHours(48);

    /** Same free-text cap the booking flow uses (the columns are TEXT). */
    private static final int MAX_REASON_LENGTH = 1000;

    static final String ALREADY_PENDING_MESSAGE =
            "A reschedule proposal is already pending for this booking";

    private final RescheduleProposalRepository proposals;
    private final BookingRepository bookings;
    private final GuideProfileRepository guides;
    private final GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    private final GuideBookingSettingsRepository settings;

    public RescheduleService(
            RescheduleProposalRepository proposals,
            BookingRepository bookings,
            GuideProfileRepository guides,
            GuideAvailabilityOccurrenceRepository availabilityOccurrences,
            GuideBookingSettingsRepository settings) {
        this.proposals = proposals;
        this.bookings = bookings;
        this.guides = guides;
        this.availabilityOccurrences = availabilityOccurrences;
        this.settings = settings;
    }

    /**
     * Create a PENDING_COUNTERPARTY proposal to move {@code bookingId} to a new start time.
     * Idempotent replay: if the SAME party already has a pending proposal for the SAME start, that
     * proposal is returned instead of a 409 — so a retried request doesn't fail on its own success.
     */
    @Transactional
    public RescheduleProposalResponse propose(
            UserEntity caller, UUID bookingId, CreateRescheduleProposalRequest req) {
        BookingEntity booking =
                bookings.findById(bookingId)
                        .orElseThrow(() -> new NotFoundException("Booking not found"));
        BookingActor requestedBy = resolveActor(caller, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only a confirmed booking can be rescheduled");
        }

        Instant now = Instant.now();
        if (!booking.getScheduledStartAt().isAfter(now)) {
            throw new ConflictException("A booking that has already started cannot be rescheduled");
        }

        Instant proposedStart = parseProposedStart(req.proposedStartAt());
        requireReasonWithinCap(req.reason());
        if (proposedStart.equals(booking.getScheduledStartAt())) {
            throw new ValidationException(
                    "The proposed time is the same as the booking's current time");
        }

        GuideBookingSettingsEntity guideSettings = loadSettings(booking.getGuideId());
        requireWithinNoticeAndAdvance(proposedStart, now, guideSettings);

        // The proposed tour keeps the booking's duration; the client never supplies an end.
        Duration tourDuration =
                Duration.between(booking.getScheduledStartAt(), booking.getScheduledEndAt());
        Instant proposedEnd = proposedStart.plus(tourDuration);

        Optional<RescheduleProposalEntity> active =
                proposals.findByBookingIdAndStatus(
                        bookingId, RescheduleStatus.PENDING_COUNTERPARTY);
        if (active.isPresent()) {
            RescheduleProposalEntity existing = active.get();
            boolean sameReplay =
                    existing.getRequestedBy() == requestedBy
                            && existing.getProposedStartAt().equals(proposedStart);
            if (sameReplay) {
                return toResponse(existing); // idempotent
            }
            throw new ConflictException(ALREADY_PENDING_MESSAGE);
        }

        requireSlotAvailable(booking, proposedStart, proposedEnd, guideSettings);

        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(booking.getId());
        p.setRequestedBy(requestedBy);
        p.setRequestedByUserId(caller.getId());
        p.setProposedStartAt(proposedStart);
        p.setProposedEndAt(proposedEnd);
        p.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        p.setFeeCents(0L);
        p.setPriceDiffCents(0L);
        p.setExpiresAt(computeExpiry(now, booking));
        try {
            // Flush now so a concurrent proposer racing past the pre-check above hits the
            // partial unique index uq_reschedule_active here, inside this try.
            proposals.saveAndFlush(p);
        } catch (DataIntegrityViolationException raceLost) {
            throw new ConflictException(ALREADY_PENDING_MESSAGE);
        }
        return toResponse(p);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * PARTICIPANT if the caller owns the booking, GUIDE if the caller is the booking's guide
     * (booking.guideId is guide_profiles.id → resolve to the profile's userId), otherwise 404 —
     * indistinguishable from a nonexistent booking.
     */
    private BookingActor resolveActor(UserEntity caller, BookingEntity booking) {
        if (booking.getParticipantUserId().equals(caller.getId())) {
            return BookingActor.PARTICIPANT;
        }
        boolean isBookingsGuide =
                guides.findById(booking.getGuideId())
                        .map(g -> g.getUserId().equals(caller.getId()))
                        .orElse(false);
        if (isBookingsGuide) {
            return BookingActor.GUIDE;
        }
        throw new NotFoundException("Booking not found");
    }

    /**
     * The proposed interval must be inside the guide's materialized availability (containment —
     * same check {@link BookingService} runs at create/checkout) and must not conflict with any
     * OTHER slot-holding booking of the guide or the caller-side participant. The booking being
     * moved is excluded from both overlap probes: shifting a tour 30 minutes overlaps its own
     * current reservation, which is not a conflict. Per the ticket, these are state conflicts →
     * 409, not 422.
     */
    private void requireSlotAvailable(
            BookingEntity booking,
            Instant proposedStart,
            Instant proposedEnd,
            GuideBookingSettingsEntity guideSettings) {
        if (!availabilityOccurrences.existsContaining(
                booking.getGuideId(), proposedStart, proposedEnd)) {
            throw new ConflictException("The proposed time is outside the guide's availability");
        }
        Instant reservedStart =
                proposedStart.minus(Duration.ofMinutes(guideSettings.getBufferBeforeMin()));
        Instant reservedEnd =
                proposedEnd.plus(Duration.ofMinutes(guideSettings.getBufferAfterMin()));
        if (bookings
                .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                        booking.getId(),
                        booking.getGuideId(),
                        BookingService.SLOT_HOLDING_STATUSES,
                        reservedEnd,
                        reservedStart)) {
            throw new ConflictException("The guide already has a booking at the proposed time");
        }
        if (bookings
                .existsByIdNotAndParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                        booking.getId(),
                        booking.getParticipantUserId(),
                        BookingService.SLOT_HOLDING_STATUSES,
                        proposedEnd,
                        proposedStart)) {
            throw new ConflictException(
                    "The participant already has a booking that overlaps the proposed time");
        }
    }

    /** 48h response window, capped at the booking's current start (see the constant's javadoc). */
    private static Instant computeExpiry(Instant now, BookingEntity booking) {
        Instant windowEnd = now.plus(COUNTERPARTY_RESPONSE_WINDOW);
        return windowEnd.isBefore(booking.getScheduledStartAt())
                ? windowEnd
                : booking.getScheduledStartAt();
    }

    private static Instant parseProposedStart(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("proposedStartAt is required");
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ex) {
            throw new ValidationException(
                    "proposedStartAt must be an ISO-8601 instant, e.g. 2026-08-05T17:00:00Z");
        }
    }

    /** Validated for contract stability; not persisted in the MVP (see class javadoc). */
    private static void requireReasonWithinCap(String reason) {
        if (reason != null && reason.trim().length() > MAX_REASON_LENGTH) {
            throw new ValidationException(
                    "reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
    }

    private GuideBookingSettingsEntity loadSettings(UUID guideId) {
        return settings.findByGuideId(guideId).orElseGet(GuideBookingSettingsEntity::new);
    }

    /**
     * Same guide-configurable minimum-notice / maximum-advance window {@link BookingService}
     * enforces on new bookings, applied to the PROPOSED start (schema defaults: 24h notice, 30-day
     * advance). Out-of-window is a 422 per the ticket (BOOKING_NOTICE_TOO_SHORT), unlike the
     * state-conflict 409s.
     */
    private static void requireWithinNoticeAndAdvance(
            Instant proposedStart, Instant now, GuideBookingSettingsEntity guideSettings) {
        Duration minNotice = Duration.ofMinutes(guideSettings.getMinNoticeMin());
        if (proposedStart.isBefore(now.plus(minNotice))) {
            throw new ValidationException(
                    "The proposed time needs at least "
                            + formatNoticeWindow(minNotice)
                            + " notice");
        }
        Duration maxAdvance = Duration.ofDays(guideSettings.getMaxAdvanceDays());
        if (proposedStart.isAfter(now.plus(maxAdvance))) {
            throw new ValidationException(
                    "The proposed time can be at most " + maxAdvance.toDays() + " days in advance");
        }
    }

    /** Whole hours read as "{n} hour(s)"; anything else as "{n} minutes" (guide-configurable). */
    private static String formatNoticeWindow(Duration minNotice) {
        long minutes = minNotice.toMinutes();
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + " minutes";
    }

    private static RescheduleProposalResponse toResponse(RescheduleProposalEntity p) {
        return new RescheduleProposalResponse(
                p.getId().toString(),
                p.getBookingId().toString(),
                p.getRequestedBy().name(),
                p.getStatus().name(),
                p.getProposedStartAt().toString(),
                p.getProposedEndAt().toString(),
                p.getFeeCents(),
                p.getPriceDiffCents(),
                p.getExpiresAt().toString());
    }
}
