package com.CampusToursLive.domain.reschedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingActor;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link RescheduleService#propose}. Covers the CTL-50 Definition of Done scenarios:
 * in-window success, outside-notice, slot-taken, duplicate-active, not-confirmed, not-owner — plus
 * availability containment, guide-as-proposer, and idempotent replay.
 */
@ExtendWith(MockitoExtension.class)
class RescheduleServiceTest {

    @Mock RescheduleProposalRepository proposals;
    @Mock BookingRepository bookings;
    @Mock GuideProfileRepository guides;
    @Mock GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    @Mock GuideBookingSettingsRepository settings;

    private RescheduleService service() {
        return new RescheduleService(
                proposals, bookings, guides, availabilityOccurrences, settings);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileEntity guide(UUID profileId, UUID userId) {
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(profileId);
        g.setUserId(userId);
        return g;
    }

    private static BookingEntity confirmedBooking(
            UUID bookingId, UUID participantId, UUID guideProfileId, Instant start) {
        BookingEntity b = new BookingEntity();
        b.setId(bookingId);
        b.setBookingNumber("BK-TEST0001");
        b.setParticipantUserId(participantId);
        b.setGuideId(guideProfileId);
        b.setTourOfferingId(UUID.randomUUID());
        b.setUniversityId(UUID.randomUUID());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(start);
        b.setScheduledEndAt(start.plus(60, ChronoUnit.MINUTES));
        b.setReservedStartAt(start);
        b.setReservedEndAt(start.plus(75, ChronoUnit.MINUTES));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setCurrency("USD");
        return b;
    }

    private void stubHappyPathSlotChecks() {
        when(settings.findByGuideId(any())).thenReturn(Optional.empty()); // schema defaults
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(true);
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByIdNotAndParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(proposals.findByBookingIdAndStatus(any(), eq(RescheduleStatus.PENDING_COUNTERPARTY)))
                .thenReturn(Optional.empty());
        when(proposals.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void propose_inWindow_persistsPendingProposal_asParticipant() {
        UUID participantId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        BookingEntity booking =
                confirmedBooking(bookingId, participantId, guideProfileId, currentStart);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        stubHappyPathSlotChecks();

        RescheduleProposalResponse resp =
                service()
                        .propose(
                                user(participantId),
                                bookingId,
                                new CreateRescheduleProposalRequest(
                                        proposedStart.toString(), "America/Los_Angeles", "moved"));

        assertEquals(bookingId.toString(), resp.bookingId());
        assertEquals("PARTICIPANT", resp.requestedBy());
        assertEquals("PENDING_COUNTERPARTY", resp.status());
        assertEquals(proposedStart.toString(), resp.proposedStartAt());
        assertEquals(proposedStart.plus(60, ChronoUnit.MINUTES).toString(), resp.proposedEndAt());
        assertEquals(0L, resp.feeCents());
        assertEquals(0L, resp.priceDiffCents());
        assertNotNull(resp.expiresAt());

        ArgumentCaptor<RescheduleProposalEntity> cap =
                ArgumentCaptor.forClass(RescheduleProposalEntity.class);
        verify(proposals).saveAndFlush(cap.capture());
        assertEquals(BookingActor.PARTICIPANT, cap.getValue().getRequestedBy());
        assertEquals(participantId, cap.getValue().getRequestedByUserId());
    }

    @Test
    void propose_asGuide_setsRequestedByGuide() {
        UUID participantId = UUID.randomUUID();
        UUID guideUserId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        BookingEntity booking =
                confirmedBooking(bookingId, participantId, guideProfileId, currentStart);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guide(guideProfileId, guideUserId)));
        stubHappyPathSlotChecks();

        RescheduleProposalResponse resp =
                service()
                        .propose(
                                user(guideUserId),
                                bookingId,
                                new CreateRescheduleProposalRequest(
                                        proposedStart.toString(), null, null));

        assertEquals("GUIDE", resp.requestedBy());
    }

    // ── DoD failure scenarios ────────────────────────────────────────────────

    @Test
    void propose_outsideNotice_is422() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        // Default min notice is 24h — 2h from now is too soon.
        Instant proposedStart =
                Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        currentStart)));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        proposedStart.toString(), null, null)));
        assertTrue(ex.getMessage().contains("notice"));
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void propose_slotTaken_is409() {
        UUID participantId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId, participantId, guideProfileId, currentStart)));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(true);
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                eq(bookingId),
                                eq(guideProfileId),
                                eq(BookingService.SLOT_HOLDING_STATUSES),
                                any(),
                                any()))
                .thenReturn(true);

        ConflictException ex =
                assertThrows(
                        ConflictException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        proposedStart.toString(), null, null)));
        assertTrue(ex.getMessage().contains("guide already has a booking"));
    }

    @Test
    void propose_outsideAvailability_is409() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        currentStart)));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(false);

        ConflictException ex =
                assertThrows(
                        ConflictException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        proposedStart.toString(), null, null)));
        assertTrue(ex.getMessage().contains("availability"));
    }

    @Test
    void propose_duplicateActive_is409() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant otherStart = Instant.now().plus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        RescheduleProposalEntity existing = new RescheduleProposalEntity();
        existing.setId(UUID.randomUUID());
        existing.setBookingId(bookingId);
        existing.setRequestedBy(BookingActor.GUIDE);
        existing.setProposedStartAt(otherStart);
        existing.setProposedEndAt(otherStart.plus(60, ChronoUnit.MINUTES));
        existing.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        existing.setFeeCents(0L);
        existing.setPriceDiffCents(0L);
        existing.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        currentStart)));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(Optional.of(existing));

        ConflictException ex =
                assertThrows(
                        ConflictException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        proposedStart.toString(), null, null)));
        assertEquals(RescheduleService.ALREADY_PENDING_MESSAGE, ex.getMessage());
    }

    @Test
    void propose_sameReplay_isIdempotent() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        RescheduleProposalEntity existing = new RescheduleProposalEntity();
        existing.setId(UUID.randomUUID());
        existing.setBookingId(bookingId);
        existing.setRequestedBy(BookingActor.PARTICIPANT);
        existing.setProposedStartAt(proposedStart);
        existing.setProposedEndAt(proposedStart.plus(60, ChronoUnit.MINUTES));
        existing.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        existing.setFeeCents(0L);
        existing.setPriceDiffCents(0L);
        existing.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        currentStart)));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(Optional.of(existing));

        RescheduleProposalResponse resp =
                service()
                        .propose(
                                user(participantId),
                                bookingId,
                                new CreateRescheduleProposalRequest(
                                        proposedStart.toString(), null, null));

        assertEquals(existing.getId().toString(), resp.id());
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void propose_notConfirmed_is409() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingEntity draft =
                confirmedBooking(
                        bookingId,
                        participantId,
                        UUID.randomUUID(),
                        Instant.now().plus(5, ChronoUnit.DAYS));
        draft.setStatus(BookingStatus.DRAFT);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(draft));

        ConflictException ex =
                assertThrows(
                        ConflictException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        Instant.now()
                                                                .plus(7, ChronoUnit.DAYS)
                                                                .toString(),
                                                        null,
                                                        null)));
        assertTrue(ex.getMessage().toLowerCase().contains("confirmed"));
    }

    @Test
    void propose_notOwner_is404() {
        UUID bookingId = UUID.randomUUID();
        UUID guideProfileId = UUID.randomUUID();
        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        UUID.randomUUID(),
                                        guideProfileId,
                                        Instant.now().plus(5, ChronoUnit.DAYS))));
        when(guides.findById(guideProfileId))
                .thenReturn(Optional.of(guide(guideProfileId, UUID.randomUUID())));

        assertThrows(
                NotFoundException.class,
                () ->
                        service()
                                .propose(
                                        user(UUID.randomUUID()),
                                        bookingId,
                                        new CreateRescheduleProposalRequest(
                                                Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                                                null,
                                                null)));
    }

    @Test
    void propose_concurrentDuplicate_mapsConstraintTo409() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant currentStart =
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant proposedStart =
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        currentStart)));
        stubHappyPathSlotChecks();
        when(proposals.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        ConflictException ex =
                assertThrows(
                        ConflictException.class,
                        () ->
                                service()
                                        .propose(
                                                user(participantId),
                                                bookingId,
                                                new CreateRescheduleProposalRequest(
                                                        proposedStart.toString(), null, null)));
        assertEquals(RescheduleService.ALREADY_PENDING_MESSAGE, ex.getMessage());
    }

    @Test
    void propose_missingStart_is422() {
        UUID participantId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookings.findById(bookingId))
                .thenReturn(
                        Optional.of(
                                confirmedBooking(
                                        bookingId,
                                        participantId,
                                        UUID.randomUUID(),
                                        Instant.now().plus(5, ChronoUnit.DAYS))));

        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .propose(
                                        user(participantId),
                                        bookingId,
                                        new CreateRescheduleProposalRequest(null, null, null)));
    }
}
