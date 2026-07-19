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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Compact unit coverage for {@link RescheduleService#propose} (CTL-50). */
@ExtendWith(MockitoExtension.class)
class RescheduleServiceTest {

    @Mock RescheduleProposalRepository proposals;
    @Mock BookingRepository bookings;
    @Mock GuideProfileRepository guides;
    @Mock GuideAvailabilityOccurrenceRepository availabilityOccurrences;
    @Mock GuideBookingSettingsRepository settings;

    private final UUID participantId = UUID.randomUUID();
    private final UUID guideProfileId = UUID.randomUUID();
    private final UUID guideUserId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final Instant currentStart =
            Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    private final Instant proposedStart =
            Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

    private RescheduleService service;

    @BeforeEach
    void setUp() {
        service =
                new RescheduleService(
                        proposals, bookings, guides, availabilityOccurrences, settings);
    }

    @Test
    void propose_happyPath_asParticipant() {
        stubBooking(confirmed());
        stubSlotOk();

        RescheduleProposalResponse resp =
                service.propose(user(participantId), bookingId, req(proposedStart));

        assertEquals("PARTICIPANT", resp.requestedBy());
        assertEquals("PENDING_COUNTERPARTY", resp.status());
        assertEquals(0L, resp.feeCents());
        verify(proposals).saveAndFlush(any());
    }

    @Test
    void propose_asGuide_andIdempotentReplay() {
        stubBooking(confirmed());
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideProfileId);
        g.setUserId(guideUserId);
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));
        stubSlotOk();
        assertEquals(
                "GUIDE",
                service.propose(user(guideUserId), bookingId, req(proposedStart)).requestedBy());

        RescheduleProposalEntity existing = pending(BookingActor.PARTICIPANT, proposedStart);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(confirmed()));
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(Optional.of(existing));
        assertEquals(
                existing.getId().toString(),
                service.propose(user(participantId), bookingId, req(proposedStart)).id());
    }

    @Test
    void propose_rejectsInvalidStateAndActor() {
        BookingEntity draft = confirmed();
        draft.setStatus(BookingStatus.DRAFT);
        stubBooking(draft);
        assertThrows(
                ConflictException.class,
                () -> service.propose(user(participantId), bookingId, req(proposedStart)));

        stubBooking(confirmed());
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideProfileId);
        g.setUserId(UUID.randomUUID());
        when(guides.findById(guideProfileId)).thenReturn(Optional.of(g));
        assertThrows(
                NotFoundException.class,
                () -> service.propose(user(UUID.randomUUID()), bookingId, req(proposedStart)));
    }

    @Test
    void propose_rejectsWindowAndSlotConflicts() {
        stubBooking(confirmed());
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        assertThrows(
                ValidationException.class,
                () ->
                        service.propose(
                                user(participantId),
                                bookingId,
                                req(Instant.now().plus(2, ChronoUnit.HOURS))));

        when(proposals.findByBookingIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(false);
        assertThrows(
                ConflictException.class,
                () -> service.propose(user(participantId), bookingId, req(proposedStart)));

        when(availabilityOccurrences.existsContaining(any(), any(), any())).thenReturn(true);
        when(bookings
                        .existsByIdNotAndGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                any(), any(), any(), any(), any()))
                .thenReturn(true);
        assertThrows(
                ConflictException.class,
                () -> service.propose(user(participantId), bookingId, req(proposedStart)));
    }

    @Test
    void propose_rejectsDuplicateAndRace() {
        stubBooking(confirmed());
        when(settings.findByGuideId(any())).thenReturn(Optional.empty());
        when(proposals.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING_COUNTERPARTY))
                .thenReturn(
                        Optional.of(
                                pending(
                                        BookingActor.GUIDE,
                                        proposedStart.plus(1, ChronoUnit.DAYS))));
        assertThrows(
                ConflictException.class,
                () -> service.propose(user(participantId), bookingId, req(proposedStart)));

        stubSlotOk();
        when(proposals.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));
        assertThrows(
                ConflictException.class,
                () -> service.propose(user(participantId), bookingId, req(proposedStart)));
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

    private BookingEntity confirmed() {
        BookingEntity b = new BookingEntity();
        b.setId(bookingId);
        b.setBookingNumber("BK-TEST0001");
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

    private static RescheduleProposalEntity pending(BookingActor by, Instant start) {
        RescheduleProposalEntity p = new RescheduleProposalEntity();
        p.setId(UUID.randomUUID());
        p.setBookingId(UUID.randomUUID());
        p.setRequestedBy(by);
        p.setProposedStartAt(start);
        p.setProposedEndAt(start.plus(60, ChronoUnit.MINUTES));
        p.setStatus(RescheduleStatus.PENDING_COUNTERPARTY);
        p.setFeeCents(0L);
        p.setPriceDiffCents(0L);
        p.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        return p;
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static CreateRescheduleProposalRequest req(Instant start) {
        return new CreateRescheduleProposalRequest(start.toString(), null, null);
    }
}
