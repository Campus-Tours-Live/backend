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

/** CTL-50: propose moving a CONFIRMED booking. Resolve is CTL-51. */
@Service
public class RescheduleService {

    static final Duration COUNTERPARTY_RESPONSE_WINDOW = Duration.ofHours(48);
    private static final int MAX_REASON_LENGTH = 1000;

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

    /** Propose; same party + same start replays the active proposal, else active → 409. */
    @Transactional
    public RescheduleProposalResponse propose(
            UUID callerUserId, UUID bookingId, CreateRescheduleProposalRequest req) {
        BookingEntity booking =
                bookings.findById(bookingId)
                        .orElseThrow(() -> new NotFoundException("Booking not found"));
        BookingActor requestedBy = resolveActor(callerUserId, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ConflictException.bookingNotConfirmedForReschedule();
        }

        Instant now = Instant.now();
        if (!booking.getScheduledStartAt().isAfter(now)) {
            throw ConflictException.bookingAlreadyStarted();
        }

        Instant proposedStart = parseProposedStart(req.proposedStartAt());
        requireReasonWithinCap(req.reason());
        if (proposedStart.equals(booking.getScheduledStartAt())) {
            throw new ValidationException(
                    "The proposed time is the same as the booking's current time");
        }

        GuideBookingSettingsEntity guideSettings = loadSettings(booking.getGuideId());
        requireWithinNoticeAndAdvance(proposedStart, now, guideSettings);

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
                return toResponse(existing);
            }
            throw ConflictException.rescheduleAlreadyPending();
        }

        requireSlotAvailable(booking, proposedStart, proposedEnd, guideSettings);

        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(booking.getId());
        p.setRequestedBy(requestedBy);
        p.setRequestedByUserId(callerUserId);
        p.setProposedStartAt(proposedStart);
        p.setProposedEndAt(proposedEnd);
        p.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        p.setFeeCents(0L);
        p.setPriceDiffCents(0L);
        p.setExpiresAt(computeExpiry(now, booking));
        try {
            proposals.saveAndFlush(p);
        } catch (DataIntegrityViolationException raceLost) {
            throw ConflictException.rescheduleAlreadyPending();
        }
        return toResponse(p);
    }

    private BookingActor resolveActor(UUID callerUserId, BookingEntity booking) {
        if (booking.getParticipantUserId().equals(callerUserId)) {
            return BookingActor.PARTICIPANT;
        }
        boolean isBookingsGuide =
                guides.findById(booking.getGuideId())
                        .map(g -> g.getUserId().equals(callerUserId))
                        .orElse(false);
        if (isBookingsGuide) {
            return BookingActor.GUIDE;
        }
        throw new NotFoundException("Booking not found");
    }

    private void requireSlotAvailable(
            BookingEntity booking,
            Instant proposedStart,
            Instant proposedEnd,
            GuideBookingSettingsEntity guideSettings) {
        if (!availabilityOccurrences.existsContaining(
                booking.getGuideId(), proposedStart, proposedEnd)) {
            throw ConflictException.proposedOutsideAvailability();
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
            throw ConflictException.guideSlotConflict();
        }
        if (bookings
                .existsByIdNotAndParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                        booking.getId(),
                        booking.getParticipantUserId(),
                        BookingService.SLOT_HOLDING_STATUSES,
                        proposedEnd,
                        proposedStart)) {
            throw ConflictException.participantSlotConflict();
        }
    }

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

    private static void requireReasonWithinCap(String reason) {
        if (reason != null && reason.trim().length() > MAX_REASON_LENGTH) {
            throw new ValidationException(
                    "reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
    }

    private GuideBookingSettingsEntity loadSettings(UUID guideId) {
        return settings.findByGuideId(guideId).orElseGet(GuideBookingSettingsEntity::new);
    }

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
