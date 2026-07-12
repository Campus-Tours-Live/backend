package com.CampusToursLive.domain.booking;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceEntity;
import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Participant-facing slot generation (CTL-54 Task 8) — the PRIMARY consumer of the materialized
 * availability occurrences (Task 2/3): {@code occurrence / offered duration - existing bookings
 * (reserved) - notice/advance window} = the concrete slots a participant can actually book for one
 * offering.
 *
 * <p><b>Reuse, not reinvention.</b> A slot listed here as "free" must actually be bookable — so the
 * would-be RESERVED interval of a candidate slot is computed with the EXACT same buffer math {@link
 * BookingService} uses when it creates a real booking: the guide's OWN {@code
 * guide_booking_settings} {@code bufferBeforeMin}/{@code bufferAfterMin} (falling back to the
 * schema defaults when the guide has no settings row — see {@link
 * com.CampusToursLive.domain.availability.GuideBookingSettingsEntity}'s javadoc), not a hardcoded
 * constant. The existing bookings subtracted are every {@link BookingService#SLOT_HOLDING_STATUSES}
 * status — not just CONFIRMED (that narrower, CONFIRMED-only view belongs to Task 7's "affected
 * bookings" warning, which answers a different question and is not reusable here).
 *
 * <p>{@code from}/{@code to} optionally narrow the considered occurrences to those intersecting
 * {@code [from, to)} (ISO {@code yyyy-MM-dd}), mirroring {@code AvailabilityReadService} (Task 5b).
 */
@Service
public class SlotGenerationService {

    private final TourOfferingRepository offerings;
    private final GuideAvailabilityOccurrenceRepository occurrences;
    private final BookingRepository bookings;
    private final GuideBookingSettingsRepository settings;
    private final Clock clock;

    /**
     * Production constructor — a system-UTC clock (no {@code Clock} bean needed in the context).
     */
    @Autowired
    public SlotGenerationService(
            TourOfferingRepository offerings,
            GuideAvailabilityOccurrenceRepository occurrences,
            BookingRepository bookings,
            GuideBookingSettingsRepository settings) {
        this(offerings, occurrences, bookings, settings, Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to pin "now" for the notice/advance window. */
    SlotGenerationService(
            TourOfferingRepository offerings,
            GuideAvailabilityOccurrenceRepository occurrences,
            BookingRepository bookings,
            GuideBookingSettingsRepository settings,
            Clock clock) {
        this.offerings = offerings;
        this.occurrences = occurrences;
        this.bookings = bookings;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * The concrete bookable slots for one offering, ascending, UTC. {@code from}/{@code to} are
     * optional ISO {@code yyyy-MM-dd} bounds narrowing which occurrences are sliced (same
     * half-open-window convention as {@code AvailabilityReadService}); when both are absent every
     * materialized occurrence is considered.
     *
     * <p>A nonexistent, non-ACTIVE, or otherwise non-discoverable offering (inactive guide/
     * university) is a 404 — mirrors {@code TourDiscoveryService#getById}, the existing
     * participant-facing single-offering read. A discoverable offering with no materialized
     * occurrences yet is NOT a 404 — it is simply zero bookable slots.
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> getBookableSlots(UUID offeringId, String from, String to) {
        TourOfferingEntity offering =
                offerings
                        .findDiscoverableById(offeringId)
                        .orElseThrow(() -> new NotFoundException("Tour not found"));
        UUID guideId = offering.getGuideId();
        Duration slotLength = Duration.ofMinutes(offering.getDurationMin());

        Instant windowStart = parseWindowBound(from, "from");
        Instant windowEnd = parseWindowBound(to, "to");
        if (windowStart != null && windowEnd != null && !windowEnd.isAfter(windowStart)) {
            throw new ValidationException("to must be after from");
        }

        List<GuideAvailabilityOccurrenceEntity> occs =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .filter(o -> intersectsWindow(o, windowStart, windowEnd))
                        .toList();
        if (occs.isEmpty()) {
            return List.of();
        }

        List<Slot> candidates = sliceIntoSlots(occs, slotLength);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Loaded ONCE and threaded through both steps below: the same row drives the buffer math
        // (subtractHeldBookings) and the notice/advance window (applyNoticeAndAdvanceWindow), so
        // there is exactly one settings read per request and no risk of the two steps seeing a
        // guide's settings change mid-computation.
        GuideBookingSettingsEntity guideSettings =
                settings.findByGuideId(guideId).orElseGet(GuideBookingSettingsEntity::new);

        candidates = subtractHeldBookings(guideId, candidates, guideSettings);
        candidates = applyNoticeAndAdvanceWindow(candidates, guideSettings);

        return candidates.stream().map(s -> new SlotResponse(s.start(), s.end())).toList();
    }

    // ---------------------------------------------------------------------------
    // Slicing: occurrence / offered duration.
    // ---------------------------------------------------------------------------

    /**
     * Slices every occurrence into back-to-back {@code slotLength} slots: {@code [oStart,
     * oStart+len)}, {@code [oStart+len, oStart+2*len)}, ... while the slot END is {@code <= oEnd}.
     * A partial tail shorter than {@code slotLength} is dropped, never returned as a slot.
     */
    private static List<Slot> sliceIntoSlots(
            List<GuideAvailabilityOccurrenceEntity> occs, Duration slotLength) {
        List<Slot> slots = new ArrayList<>();
        for (GuideAvailabilityOccurrenceEntity o : occs) {
            Instant slotStart = o.getDuringStartAt();
            Instant occurrenceEnd = o.getDuringEndAt();
            while (true) {
                Instant slotEnd = slotStart.plus(slotLength);
                if (slotEnd.isAfter(occurrenceEnd)) {
                    break; // partial tail -- not a slot
                }
                slots.add(new Slot(slotStart, slotEnd));
                slotStart = slotEnd;
            }
        }
        return slots;
    }

    // ---------------------------------------------------------------------------
    // Subtraction: existing held bookings, compared by RESERVED interval.
    // ---------------------------------------------------------------------------

    /**
     * Drops any candidate whose would-be RESERVED interval (same buffer math as {@link
     * BookingService}: the guide's OWN {@code bufferBeforeMin}/{@code bufferAfterMin}) would
     * overlap an existing held booking's actual reserved interval.
     */
    private List<Slot> subtractHeldBookings(
            UUID guideId, List<Slot> candidates, GuideBookingSettingsEntity guideSettings) {
        Duration bufferBefore = Duration.ofMinutes(guideSettings.getBufferBeforeMin());
        Duration bufferAfter = Duration.ofMinutes(guideSettings.getBufferAfterMin());
        Instant queryStart = candidates.get(0).start().minus(bufferBefore);
        Instant queryEnd =
                candidates.stream()
                        .map(Slot::end)
                        .max(Instant::compareTo)
                        .orElseThrow()
                        .plus(bufferAfter);

        List<BookingEntity> held =
                bookings
                        .findByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                guideId,
                                BookingService.SLOT_HOLDING_STATUSES,
                                queryEnd,
                                queryStart);
        if (held.isEmpty()) {
            return candidates;
        }

        List<Slot> free = new ArrayList<>();
        for (Slot candidate : candidates) {
            Instant reservedStart = candidate.start().minus(bufferBefore);
            Instant reservedEnd = candidate.end().plus(bufferAfter);
            boolean taken =
                    held.stream()
                            .anyMatch(
                                    b ->
                                            b.getReservedStartAt().isBefore(reservedEnd)
                                                    && b.getReservedEndAt().isAfter(reservedStart));
            if (!taken) {
                free.add(candidate);
            }
        }
        return free;
    }

    // ---------------------------------------------------------------------------
    // Notice / max-advance window.
    // ---------------------------------------------------------------------------

    /**
     * Drops slots starting before {@code now + minNoticeMin} or after {@code now + maxAdvanceDays}.
     */
    private List<Slot> applyNoticeAndAdvanceWindow(
            List<Slot> candidates, GuideBookingSettingsEntity guideSettings) {
        Instant now = clock.instant();
        Instant noticeBound = now.plus(Duration.ofMinutes(guideSettings.getMinNoticeMin()));
        Instant advanceBound = now.plus(Duration.ofDays(guideSettings.getMaxAdvanceDays()));

        List<Slot> inWindow = new ArrayList<>();
        for (Slot candidate : candidates) {
            if (candidate.start().isBefore(noticeBound)) {
                continue;
            }
            if (candidate.start().isAfter(advanceBound)) {
                continue;
            }
            inWindow.add(candidate);
        }
        return inWindow;
    }

    // ---------------------------------------------------------------------------
    // Window-bound parsing (duplicated from AvailabilityReadService -- see its javadoc: this is
    // the second consumer of the pattern, so duplicating a 3-line helper still keeps each read
    // feature's diff self-contained; a third consumer would be the trigger to extract a shared
    // helper).
    // ---------------------------------------------------------------------------

    private static boolean intersectsWindow(
            GuideAvailabilityOccurrenceEntity occurrence, Instant windowStart, Instant windowEnd) {
        boolean startsBeforeWindowEnd =
                windowEnd == null || occurrence.getDuringStartAt().isBefore(windowEnd);
        boolean endsAfterWindowStart =
                windowStart == null || occurrence.getDuringEndAt().isAfter(windowStart);
        return startsBeforeWindowEnd && endsAfterWindowStart;
    }

    private static Instant parseWindowBound(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "Invalid " + paramName + " (expected e.g. \"2026-07-11\"): " + raw);
        }
    }

    /** A candidate slot before/after filtering -- not yet the public DTO. */
    private record Slot(Instant start, Instant end) {}
}
