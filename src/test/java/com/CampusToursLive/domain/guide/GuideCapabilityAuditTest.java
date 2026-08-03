package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.availability.GuideAvailabilityOccurrenceRepository;
import com.CampusToursLive.domain.availability.GuideBookingSettingsRepository;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingStatusHistoryRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourOfferingService;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.university.CampusImageUrls;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.GlobalExceptionHandler;
import com.CampusToursLive.web.GuideOfferingController;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * CTL-97 Task 12 — RELEASE-BLOCKING guide capability audit.
 *
 * <p>Deferred provisioning means a user can hold the GUIDE role (granted at submit time, see {@link
 * GuideService#updateProfile}) while {@code guide_status} is still {@link GuideStatus#PENDING} —
 * verification (flipping to {@link GuideStatus#VERIFIED}) happens later. The security guarantee
 * this class locks: a GUIDE+PENDING user is INERT — every guide capability with an outward effect
 * (publishing an offering, becoming bookable, having a booking created against them) must reject
 * them exactly the same way it does today, so a future refactor can't silently drop the {@code
 * guide_status == VERIFIED} gate.
 *
 * <p><b>Audited capability surface</b> (see the PR body / task report for the full table):
 *
 * <ul>
 *   <li>{@link TourOfferingService#activate} — publishing DRAFT→ACTIVE. GATED directly: throws
 *       {@link ForbiddenException} when {@code guide.status != VERIFIED} (the pre-existing baseline
 *       gate this whole feature is built around).
 *   <li>{@link BookingService#createBooking} — a participant booking a guide's tour. GATED via
 *       {@code requireApprovedGuide}: throws {@link ValidationException} when the offering's owning
 *       guide is not VERIFIED (defense-in-depth even when an offering is somehow ACTIVE while its
 *       guide is not VERIFIED — e.g. a guide demoted after activating).
 *   <li>Discovery/search exposure ({@code TourOfferingRepository.findDiscoverable(ById)}) — GATED
 *       at the query level (JPQL {@code g.status = VERIFIED}); proven against a real DB in {@code
 *       TourOfferingRepositoryTest#findDiscoverableById_excludesPendingGuideAndInactiveUniversity}
 *       and locked independently for this audit in {@code GuideCapabilityAuditIntegrationTest}.
 *   <li>Availability writes ({@code AvailabilityWriteService} — rules/exceptions/booking settings)
 *       are NOT gated on {@code guide_status} directly, but are inert while PENDING: they can only
 *       ever affect bookability through an ACTIVE offering (activate() gates VERIFIED) or through
 *       {@code BookingService.requireApprovedGuide} (also gates VERIFIED) — the same "prepare while
 *       pending, go live only once verified" precedent {@link TourOfferingService#create} already
 *       documents for DRAFT offerings. No outward effect exists without one of the two gates above.
 *   <li>Guide-driven booking accept/decline and offering duplicate/import DO NOT EXIST in this
 *       codebase yet (see {@code BookingService}'s class javadoc: "Guide accept/decline ... still
 *       deferred") — nothing to gate or test.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GuideCapabilityAuditTest {

    // ---------------------------------------------------------------------
    // Capability 1: TourOfferingService.activate (publish DRAFT -> ACTIVE)
    // ---------------------------------------------------------------------

    @Nested
    class OfferingActivation {

        @Mock TourOfferingRepository offerings;
        @Mock GuideProfileRepository guides;
        @Mock GuideUniversityRepository guideUniversities;
        @Mock UniversityRepository universities;

        private TourOfferingService service() {
            return new TourOfferingService(
                    offerings,
                    guides,
                    guideUniversities,
                    universities,
                    new CampusImageUrls("https://r2.example/"),
                    new ObjectMapper());
        }

        @Test
        void activate_rejectsGuideAndPending_evenWithAnOwnedDraftOffering() {
            UUID uid = UUID.randomUUID();
            UUID gid = UUID.randomUUID();
            GuideProfileEntity pending = new GuideProfileEntity();
            pending.setId(gid);
            pending.setStatus(GuideStatus.PENDING);
            when(guides.findByUserId(uid)).thenReturn(Optional.of(pending));

            ForbiddenException ex =
                    assertThrows(
                            ForbiddenException.class,
                            () -> service().activate(user(uid), UUID.randomUUID()));
            assertEquals(
                    "Your guide application must be approved before you can publish offerings",
                    ex.getMessage());
            // The gate fires before the offering is even looked up -- a PENDING guide cannot
            // publish ANY offering, owned or not.
            verifyNoInteractions(offerings);
        }

        @Test
        void activate_allowsGuideAndVerified_positiveControl() {
            UUID uid = UUID.randomUUID();
            UUID gid = UUID.randomUUID();
            UUID oid = UUID.randomUUID();
            GuideProfileEntity verified = new GuideProfileEntity();
            verified.setId(gid);
            verified.setStatus(GuideStatus.VERIFIED);
            when(guides.findByUserId(uid)).thenReturn(Optional.of(verified));
            TourOfferingEntity draft = new TourOfferingEntity();
            draft.setId(oid);
            draft.setGuideId(gid);
            draft.setStatus(TourStatus.DRAFT);
            when(offerings.findByIdAndGuideId(oid, gid)).thenReturn(Optional.of(draft));
            when(offerings.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TourOfferingResponse res = service().activate(user(uid), oid);
            assertEquals("ACTIVE", res.status());
        }

        private static UserEntity user(UUID id) {
            UserEntity u = new UserEntity();
            u.setId(id);
            return u;
        }
    }

    // ---------------------------------------------------------------------
    // Capability 1b: GuideOfferingController POST /guide/offerings/{id}/activate
    // (representative endpoint-level test for the release gate)
    // ---------------------------------------------------------------------

    @Nested
    class OfferingActivationEndpoint {

        @Mock CurrentUser currentUser;
        @Mock TourOfferingService offerings;

        private MockMvc mvc() {
            GuideOfferingController controller =
                    new GuideOfferingController(currentUser, offerings);
            return MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
        }

        @Test
        void activate_endpoint_returns403_forGuideAndPending() throws Exception {
            UserEntity u = new UserEntity();
            u.setId(UUID.randomUUID());
            UUID offeringId = UUID.randomUUID();
            when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
            when(offerings.activate(u, offeringId))
                    .thenThrow(
                            new ForbiddenException(
                                    "Your guide application must be approved before you can"
                                            + " publish offerings"));

            mvc().perform(post("/guide/offerings/{id}/activate", offeringId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(
                            jsonPath("$.title")
                                    .value(
                                            "Your guide application must be approved before you"
                                                    + " can publish offerings"));
        }
    }

    // ---------------------------------------------------------------------
    // Capability 2: BookingService.createBooking (a participant booking a guide's tour)
    // ---------------------------------------------------------------------

    @Nested
    class BookingCreationAgainstGuide {

        @Mock BookingRepository bookings;
        @Mock TourOfferingRepository offerings;
        @Mock GuideProfileRepository guides;
        @Mock UserRepository users;
        @Mock UniversityRepository universities;
        @Mock BookingStatusHistoryRepository statusHistory;
        @Mock GuideAvailabilityOccurrenceRepository availabilityOccurrences;
        @Mock GuideBookingSettingsRepository settings;

        private BookingService service() {
            return new BookingService(
                    bookings,
                    offerings,
                    guides,
                    users,
                    universities,
                    statusHistory,
                    availabilityOccurrences,
                    settings);
        }

        @Test
        void createBooking_rejectsBookingAgainstGuideAndPending_evenIfOfferingIsActive() {
            UserEntity participant = new UserEntity();
            participant.setId(UUID.randomUUID());
            UUID offeringId = UUID.randomUUID();
            UUID guideProfileId = UUID.randomUUID();
            UUID universityId = UUID.randomUUID();

            // The offering itself is ACTIVE (e.g. it was activated while the guide was VERIFIED,
            // then the guide's status later reverted to PENDING) -- the guide-level check must
            // still be the backstop.
            TourOfferingEntity o = new TourOfferingEntity();
            o.setId(offeringId);
            o.setGuideId(guideProfileId);
            o.setUniversityId(universityId);
            o.setStatus(TourStatus.ACTIVE);
            when(offerings.findById(offeringId)).thenReturn(Optional.of(o));

            GuideProfileEntity pendingGuide = new GuideProfileEntity();
            pendingGuide.setId(guideProfileId);
            pendingGuide.setUserId(UUID.randomUUID());
            pendingGuide.setStatus(GuideStatus.PENDING);
            when(guides.findById(guideProfileId)).thenReturn(Optional.of(pendingGuide));

            // The university is ACTIVE -- so the ONLY possible rejection source is the guide gate.
            // If the guide_status == VERIFIED check were ever removed, requireActiveUniversity
            // would pass and this test would fail to see the expected rejection
            // (mutation-sensitive).
            // Stubbed leniently: with the guide gate intact, execution never reaches this lookup.
            UniversityEntity university = new UniversityEntity();
            university.setId(universityId);
            university.setName("Test University");
            university.setStatus(UniversityStatus.ACTIVE);
            lenient().when(universities.findById(universityId)).thenReturn(Optional.of(university));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service()
                                            .createBooking(
                                                    participant,
                                                    new CreateBookingRequest(
                                                            offeringId.toString(), null, null)));
            assertEquals("This tour is not currently bookable", ex.getMessage());
            verifyNoInteractions(bookings);
        }

        @Test
        void createBooking_allowsBookingAgainstGuideAndVerified_positiveControl() {
            UserEntity participant = new UserEntity();
            participant.setId(UUID.randomUUID());
            UUID offeringId = UUID.randomUUID();
            UUID guideProfileId = UUID.randomUUID();
            UUID guideUserId = UUID.randomUUID();
            UUID universityId = UUID.randomUUID();

            TourOfferingEntity o = new TourOfferingEntity();
            o.setId(offeringId);
            o.setGuideId(guideProfileId);
            o.setUniversityId(universityId);
            o.setStatus(TourStatus.ACTIVE);
            o.setDurationMin(60);
            o.setPriceCents(5000L);
            o.setTitle("Campus Walk");
            when(offerings.findById(offeringId)).thenReturn(Optional.of(o));

            GuideProfileEntity verifiedGuide = new GuideProfileEntity();
            verifiedGuide.setId(guideProfileId);
            verifiedGuide.setUserId(guideUserId);
            verifiedGuide.setStatus(GuideStatus.VERIFIED);
            when(guides.findById(guideProfileId)).thenReturn(Optional.of(verifiedGuide));

            UniversityEntity u = new UniversityEntity();
            u.setId(universityId);
            u.setName("Test University");
            u.setStatus(UniversityStatus.ACTIVE);
            when(universities.findById(universityId)).thenReturn(Optional.of(u));

            when(users.findById(guideUserId))
                    .thenReturn(Optional.of(userWithName(guideUserId, "Jane Guide")));

            lenient()
                    .when(
                            availabilityOccurrences.existsContaining(
                                    eq(guideProfileId), any(), any()))
                    .thenReturn(true);
            lenient().when(settings.findByGuideId(guideProfileId)).thenReturn(Optional.empty());
            when(bookings
                            .existsByGuideIdAndStatusInAndReservedStartAtLessThanAndReservedEndAtGreaterThan(
                                    any(), any(), any(), any()))
                    .thenReturn(false);
            when(bookings
                            .existsByParticipantUserIdAndStatusInAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
                                    any(), any(), any(), any()))
                    .thenReturn(false);

            Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
            BookingDetailResponse resp =
                    service()
                            .createBooking(
                                    participant,
                                    new CreateBookingRequest(
                                            offeringId.toString(), start.toString(), null));

            assertEquals("WAITING_FOR_GUIDE", resp.status());
        }

        private static UserEntity userWithName(UUID id, String name) {
            UserEntity u = new UserEntity();
            u.setId(id);
            u.setDisplayName(name);
            return u;
        }
    }
}
