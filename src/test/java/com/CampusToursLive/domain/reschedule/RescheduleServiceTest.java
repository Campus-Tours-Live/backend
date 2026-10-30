package com.CampusToursLive.domain.reschedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsEntity;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.booking.*;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.error.*;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RescheduleServiceTest {

    @Mock RescheduleProposalRepository proposals;
    @Mock BookingRepository bookings;
    @Mock GuideProfileRepository guides;
    @Mock GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    @Mock GuideBookingSettingsRepository settings;

    private final UUID participantId = UUID.randomUUID();
    private final UUID guideProfileId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final Instant currentStart = Instant.now().plus(5, ChronoUnit.DAYS);
    private final Instant proposedStart = Instant.now().plus(7, ChronoUnit.DAYS);
    private RescheduleService service;

    @BeforeEach
    void setUp() {
        service =
                new RescheduleService(
                        proposals, bookings, guides, availabilityOccurrences, settings);
    }

    @Test
    void propose_persistsReason_guideActor_andExpiryCap() {
        ready();
        assertEquals("Class moved.", propose(" Class moved. ").reason());
        ArgumentCaptor<RescheduleProposalEntity> cap =
                ArgumentCaptor.forClass(RescheduleProposalEntity.class);
        verify(proposals).saveAndFlush(cap.capture());
        assertEquals("Class moved.", cap.getValue().getReason());

        UUID guideUserId = UUID.randomUUID();
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(guide(guideUserId)));
        assertEquals("GUIDE", propose(guideUserId, proposedStart, null).requestedBy());

        BookingEntity soon = confirmed();
        soon.setScheduledStartAt(Instant.now().plus(20, ChronoUnit.HOURS));
        soon.setScheduledEndAt(soon.getScheduledStartAt().plus(60, ChronoUnit.MINUTES));
        stubBooking(soon);
        assertEquals(soon.getScheduledStartAt().toString(), propose(null).expiresAt());
    }

    @Test
    void propose_rejectsMissingOrIneligibleBooking() {
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> propose(null));
        BookingEntity draft = confirmed();
        draft.setStatus(BookingStatus.DRAFT);
        stubBooking(draft);
        assertThrows(ConflictException.class, () -> propose(null));
        BookingEntity started = confirmed();
        started.setScheduledStartAt(Instant.now().minus(1, ChronoUnit.HOURS));
        stubBooking(started);
        assertThrows(ConflictException.class, () -> propose(null));
        ready();
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(guide(UUID.randomUUID())));
        assertThrows(
                NotFoundException.class, () -> propose(UUID.randomUUID(), proposedStart, null));
    }

    @Test
    void propose_rejectsBadProposedTimeAndReason() {
        ready();
        assertThrows(ValidationException.class, () -> propose(participantId, currentStart, null));
        assertThrows(
                ValidationException.class,
                () ->
                        service.propose(
                                participantId,
                                bookingId,
                                new CreateRescheduleProposalRequest("nope", null, null)));
        assertThrows(ValidationException.class, () -> propose("x".repeat(1001)));
        GuideBookingSettingsEntity s = new GuideBookingSettingsEntity();
        s.setMinNoticeMin(90);
        s.setMaxAdvanceDays(5);
        when(settings.findByGuideId(any())).thenReturn(Optional.of(s));
        assertThrows(
                ValidationException.class,
                () -> propose(participantId, Instant.now().plus(30, ChronoUnit.MINUTES), null));
        assertThrows(
                ValidationException.class,
                () -> propose(participantId, Instant.now().plus(10, ChronoUnit.DAYS), null));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        assertThrows(
                ValidationException.class,
                () -> propose(participantId, Instant.now().plus(2, ChronoUnit.HOURS), null));
    }

    @Test
    void propose_rejectsSlotConflicts() {
        ready();
        when(proposals.findByBookingIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(false);
        assertThrows(ConflictException.class, () -> propose(null));
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(true);
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> propose(null));
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByIdNotAndParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> propose(null));
    }

    @Test
    void propose_replaysOrConflictsWhenPending() {
        ready();
        RescheduleProposalEntity pending = pending();
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(Optional.of(pending));
        assertEquals(pending.getId().toString(), propose(null).id());
        pending.setProposedStartAt(proposedStart.plus(1, ChronoUnit.DAYS));
        assertThrows(ConflictException.class, () -> propose(null));
        stubSlotOk();
        when(proposals.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));
        assertThrows(ConflictException.class, () -> propose(null));
    }

    private void ready() {
        stubBooking(confirmed());
        stubSlotOk();
    }

    private RescheduleProposalResponse propose(String reason) {
        return propose(participantId, proposedStart, reason);
    }

    private RescheduleProposalResponse propose(UUID userId, Instant start, String reason) {
        return service.propose(
                userId,
                bookingId,
                new CreateRescheduleProposalRequest(start.toString(), null, reason));
    }

    private void stubBooking(BookingEntity b) {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
    }

    private void stubSlotOk() {
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
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

    private GuideProfileEntity guide(UUID userId) {
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideProfileId);
        g.setUserId(userId);
        return g;
    }

    private RescheduleProposalEntity pending() {
        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(bookingId);
        p.setRequestedBy(BookingActor.PARTICIPANT);
        p.setProposedStartAt(proposedStart);
        p.setProposedEndAt(proposedStart.plus(60, ChronoUnit.MINUTES));
        p.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        p.setFeeCents(0L);
        p.setPriceDiffCents(0L);
        p.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        return p;
    }

    private BookingEntity confirmed() {
        BookingEntity b = new BookingEntity();
        b.setId(bookingId);
        b.setBookingNumber("BK-T");
        b.setParticipantUserId(participantId);
        b.setGuideId(guideProfileId);
        b.setTourOfferingId(UUID.randomUUID());
        b.setUniversityId(UUID.randomUUID());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(currentStart);
        b.setScheduledEndAt(currentStart.plus(60, ChronoUnit.MINUTES));
        b.setReservedStartAt(currentStart);
        b.setReservedEndAt(currentStart.plus(75, ChronoUnit.MINUTES));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setCurrency("USD");
        return b;
    }
}
