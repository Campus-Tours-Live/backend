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
import com.CampusToursLive.domain.user.UserEntity;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
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
    void propose_happyPath_guideActor_andExpiryCap() {
        stubBooking(confirmed());
        stubSlotOk();
        assertEquals("PARTICIPANT", propose(participantId, proposedStart).requestedBy());

        UUID guideUserId = UUID.randomUUID();
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideProfileId);
        g.setUserId(guideUserId);
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));
        assertEquals("GUIDE", propose(guideUserId, proposedStart).requestedBy());

        BookingEntity soon = confirmed();
        soon.setScheduledStartAt(Instant.now().plus(20, ChronoUnit.HOURS));
        soon.setScheduledEndAt(soon.getScheduledStartAt().plus(60, ChronoUnit.MINUTES));
        stubBooking(soon);
        assertEquals(
                soon.getScheduledStartAt().toString(),
                propose(participantId, proposedStart).expiresAt());
    }

    @Test
    void propose_rejectsGuards() {
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> propose(participantId, proposedStart));

        BookingEntity draft = confirmed();
        draft.setStatus(BookingStatus.DRAFT);
        stubBooking(draft);
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        BookingEntity started = confirmed();
        started.setScheduledStartAt(Instant.now().minus(1, ChronoUnit.HOURS));
        stubBooking(started);
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        stubBooking(confirmed());
        assertThrows(ValidationException.class, () -> propose(participantId, currentStart));
        assertThrows(ValidationException.class, () -> proposeRaw(participantId, null, null));
        assertThrows(ValidationException.class, () -> proposeRaw(participantId, " ", null));
        assertThrows(ValidationException.class, () -> proposeRaw(participantId, "nope", null));
        assertThrows(
                ValidationException.class,
                () -> proposeRaw(participantId, proposedStart.toString(), "x".repeat(1001)));

        GuideBookingSettingsEntity s = new GuideBookingSettingsEntity();
        s.setMinNoticeMin(90);
        s.setMaxAdvanceDays(5);
        when(settings.findByGuideId(any())).thenReturn(Optional.of(s));
        assertTrue(
                assertThrows(
                                ValidationException.class,
                                () ->
                                        proposeRaw(
                                                participantId,
                                                Instant.now()
                                                        .plus(30, ChronoUnit.MINUTES)
                                                        .toString(),
                                                "ok"))
                        .getMessage()
                        .contains("90 minutes"));
        s.setMinNoticeMin(60);
        assertTrue(
                assertThrows(
                                ValidationException.class,
                                () ->
                                        propose(
                                                participantId,
                                                Instant.now().plus(30, ChronoUnit.MINUTES)))
                        .getMessage()
                        .contains("1 hour"));
        assertThrows(
                ValidationException.class,
                () -> propose(participantId, Instant.now().plus(10, ChronoUnit.DAYS)));

        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        assertTrue(
                assertThrows(
                                ValidationException.class,
                                () ->
                                        propose(
                                                participantId,
                                                Instant.now().plus(2, ChronoUnit.HOURS)))
                        .getMessage()
                        .contains("24 hours"));
        when(proposals.findByBookingIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(false);
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(true);
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(bookings
                        .existsByIdNotAndParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        RescheduleProposalEntity pending = pending(proposedStart, BookingActor.PARTICIPANT);
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(Optional.of(pending));
        assertEquals(pending.getId().toString(), propose(participantId, proposedStart).id());

        pending.setProposedStartAt(proposedStart.plus(1, ChronoUnit.DAYS));
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        stubSlotOk();
        when(proposals.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));
        assertThrows(ConflictException.class, () -> propose(participantId, proposedStart));

        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideProfileId);
        g.setUserId(UUID.randomUUID());
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));
        assertThrows(NotFoundException.class, () -> propose(UUID.randomUUID(), proposedStart));
    }

    private RescheduleProposalResponse propose(UUID userId, Instant start) {
        return proposeRaw(userId, start.toString(), null);
    }

    private RescheduleProposalResponse proposeRaw(UUID userId, String start, String reason) {
        return service.propose(
                user(userId), bookingId, new CreateRescheduleProposalRequest(start, null, reason));
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

    private RescheduleProposalEntity pending(Instant start, BookingActor by) {
        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(bookingId);
        p.setRequestedBy(by);
        p.setProposedStartAt(start);
        p.setProposedEndAt(start.plus(60, ChronoUnit.MINUTES));
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

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }
}
