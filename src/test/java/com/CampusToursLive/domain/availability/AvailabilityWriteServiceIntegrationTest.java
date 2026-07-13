package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.booking.BookingEntity;
import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.booking.BookingStatus;
import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AffectedBookingResponse;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsUpdateRequest;
import com.CampusToursLive.web.dto.OverrideReplaceRequest;
import com.CampusToursLive.web.dto.RulesReplaceRequest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistence integration test for {@link AvailabilityWriteService} against a REAL PostgreSQL
 * (Testcontainers) — the only way to exercise the guide-profile lookup, the IDOR-safe {@code
 * findByIdAndGuideId} scoping, the settings auto-provisioning insert, the jsonb {@code
 * durations_offered} round-trip ({@link DurationsOfferedConverter}), and — critically — that every
 * write really does call through to {@link AvailabilityService#rematerialize(UUID)} and the guide's
 * materialized occurrences change accordingly. Mirrors {@code AvailabilityServiceIntegrationTest}.
 *
 * <p>Both services are constructed with a FIXED clock (today = {@code 2026-07-11}, a Saturday) so
 * the rolling horizon and the default {@code effectiveFrom} are deterministic.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityWriteServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 7, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final int TODAY_DOW = FIXED_TODAY.getDayOfWeek().getValue() % 7;

    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private AvailabilityExceptionRepository exceptions;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideAvailabilityDstNoticeRepository dstNotices;
    @Autowired private GuideBookingSettingsRepository settingsRepo;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;
    @Autowired private TourOfferingRepository offerings;
    @Autowired private BookingRepository bookings;
    @Autowired private EntityManager entityManager;

    private AvailabilityService availabilityService;
    private AvailabilityWriteService writeService;
    private UUID universityId;

    private UUID guideAUserId;
    private UUID guideAId;
    private UUID guideBId;
    private UUID offeringId;

    @BeforeEach
    void setUp() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        universityId = university.getId();

        GuideProfileEntity guideA = seedGuide("Guide A");
        guideAUserId = guideA.getUserId();
        guideAId = guideA.getId();
        guideBId = seedGuide("Guide B").getId();
        offeringId = seedOffering(guideAId).getId();

        availabilityService =
                new AvailabilityService(
                        rules,
                        exceptions,
                        occurrences,
                        dstNotices,
                        settingsRepo,
                        entityManager,
                        FIXED_CLOCK);
        writeService =
                new AvailabilityWriteService(
                        rules,
                        exceptions,
                        settingsRepo,
                        guides,
                        availabilityService,
                        entityManager,
                        bookings,
                        occurrences,
                        FIXED_CLOCK);
    }

    // ---------------------------------------------------------------------
    // Rules — happy path + rematerialize wiring.
    // ---------------------------------------------------------------------

    @Test
    void createRule_persistsWithSettingsTimezone_andRematerializes() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(
                        TODAY_DOW,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.toString(),
                        null);

        AvailabilityRuleResponse created = writeService.createRule(actingUser(guideAUserId), req);

        assertThat(created.timezone()).isEqualTo(AvailabilityService.DEFAULT_TIMEZONE);
        assertThat(created.active()).isTrue();

        List<GuideAvailabilityRuleEntity> persisted = rules.findByGuideId(guideAId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getTimezone()).isEqualTo(AvailabilityService.DEFAULT_TIMEZONE);

        // rematerialize ran in the same transaction -> an occurrence now exists for this guide.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isNotEmpty();
    }

    @Test
    void createRule_usesExistingSettingsTimezone_whenSettingsRowAlreadyExists() {
        settingsRepo.save(settings(guideAId, "America/New_York"));

        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        AvailabilityRuleResponse created = writeService.createRule(actingUser(guideAUserId), req);

        assertThat(created.timezone()).isEqualTo("America/New_York");
        // effectiveFrom defaulted to "today" (the fixed clock) since the request omitted it.
        assertThat(created.effectiveFrom()).isEqualTo(FIXED_TODAY.toString());
    }

    @Test
    void deleteRule_removesOccurrences_whenNoRulesRemain() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(
                        TODAY_DOW,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.toString(),
                        null);
        AvailabilityRuleResponse created = writeService.createRule(actingUser(guideAUserId), req);
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isNotEmpty();

        List<AvailabilityRuleResponse> remaining =
                writeService.deleteRule(actingUser(guideAUserId), UUID.fromString(created.id()));

        assertThat(remaining).isEmpty();
        assertThat(rules.findByGuideId(guideAId)).isEmpty();
        // The write re-projected: with no rules left, the guide has no occurrences either.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isEmpty();
    }

    // ---------------------------------------------------------------------
    // IDOR — 404 across guides for both rules and exceptions.
    // ---------------------------------------------------------------------

    @Test
    void updateRule_404_whenOwnedByAnotherGuide() {
        AvailabilityRuleRequest createReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), createReq);
        UUID ruleId = UUID.fromString(created.id());

        UserEntity guideBUser = actingUserForGuideProfile(guideBId);
        AvailabilityRuleRequest updateReq =
                new AvailabilityRuleRequest(2, "10:00", 45, null, null, null);

        assertThatThrownBy(() -> writeService.updateRule(guideBUser, ruleId, updateReq))
                .isInstanceOf(NotFoundException.class);

        // Untouched.
        assertThat(rules.findById(ruleId).orElseThrow().getStartLocal().toString())
                .isEqualTo("09:00");
    }

    @Test
    void deleteRule_404_whenOwnedByAnotherGuide() {
        AvailabilityRuleRequest createReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), createReq);
        UUID ruleId = UUID.fromString(created.id());

        UserEntity guideBUser = actingUserForGuideProfile(guideBId);

        assertThatThrownBy(() -> writeService.deleteRule(guideBUser, ruleId))
                .isInstanceOf(NotFoundException.class);
        assertThat(rules.findById(ruleId)).isPresent();
    }

    @Test
    void updateException_404_whenOwnedByAnotherGuide() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());

        UserEntity guideBUser = actingUserForGuideProfile(guideBId);
        AvailabilityExceptionRequest updateReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "UNAVAILABLE", "11:00", 30, "nope");

        assertThatThrownBy(() -> writeService.updateException(guideBUser, exceptionId, updateReq))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteException_404_whenOwnedByAnotherGuide() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());

        UserEntity guideBUser = actingUserForGuideProfile(guideBId);

        assertThatThrownBy(() -> writeService.deleteException(guideBUser, exceptionId))
                .isInstanceOf(NotFoundException.class);
        assertThat(exceptions.findById(exceptionId)).isPresent();
    }

    // ---------------------------------------------------------------------
    // Validation — 422s.
    // ---------------------------------------------------------------------

    @Test
    void createRule_rejectsNonPositiveWindowMin() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 0, null, null, null);
        assertThatThrownBy(() -> writeService.createRule(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(rules.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createRule_rejectsOutOfRangeDayOfWeek() {
        AvailabilityRuleRequest req = new AvailabilityRuleRequest(9, "09:00", 60, null, null, null);
        assertThatThrownBy(() -> writeService.createRule(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createRule_rejectsMissingStartLocal() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(TODAY_DOW, null, 60, null, null, null);
        assertThatThrownBy(() -> writeService.createRule(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createException_rejectsMissingKind() {
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(FIXED_TODAY.toString(), null, "10:00", 60, null);
        assertThatThrownBy(() -> writeService.createException(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateSettings_rejectsInvalidTimezone() {
        GuideBookingSettingsUpdateRequest req =
                new GuideBookingSettingsUpdateRequest(
                        null, null, null, null, null, null, null, "Not/AZone");
        assertThatThrownBy(() -> writeService.updateSettings(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
    }

    // ---------------------------------------------------------------------
    // Task 2 (CTL-54 v2.1) — weekly rule same-day + effective-range-aware overlap validation.
    // ---------------------------------------------------------------------

    @Test
    void createRule_rejectsRangeCrossingMidnight() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(MONDAY_DOW, "23:30", 60, null, null, null);

        assertThatThrownBy(() -> writeService.createRule(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(rules.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createRule_allowsRangeEndingExactlyAtMidnight() {
        AvailabilityRuleRequest req =
                new AvailabilityRuleRequest(MONDAY_DOW, "22:00", 120, null, null, null);

        AvailabilityRuleResponse created = writeService.createRule(actingUser(guideAUserId), req);

        assertThat(created.startLocal()).isEqualTo("22:00");
        assertThat(rules.findByGuideId(guideAId)).hasSize(1);
    }

    @Test
    void createRule_coalescesOverlappingSameDaySameEffectiveRange() {
        // CTL-54 coalesce: this used to 422 (T2's original overlap guard); a same-day,
        // same-effective-range ACTIVE overlap now MERGES into one rule instead of being rejected.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 60, null, null, null));

        AvailabilityRuleRequest overlapping =
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 240, null, null, null);
        AvailabilityRuleResponse merged =
                writeService.createRule(actingUser(guideAUserId), overlapping);

        // The candidate's own span (09:00-13:00) already fully contains the sibling's
        // (10:00-11:00), so the union equals the candidate's own span.
        assertThat(merged.startLocal()).isEqualTo("09:00");
        assertThat(merged.windowMin()).isEqualTo(240);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) MONDAY_DOW);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.get(0).getWindowMin()).isEqualTo(240);
    }

    @Test
    void createRule_allowsSameTimeOfDay_onADifferentDayOfWeek() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 240, null, null, null));

        int tuesdayDow = (MONDAY_DOW + 1) % 7;
        AvailabilityRuleRequest tuesday =
                new AvailabilityRuleRequest(tuesdayDow, "09:00", 240, null, null, null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), tuesday);

        assertThat(created.dayOfWeek()).isEqualTo(tuesdayDow);
        assertThat(rules.findByGuideId(guideAId)).hasSize(2);
    }

    @Test
    void createRule_allowsSameTimeOfDay_withDisjointEffectiveRanges() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        MONDAY_DOW, "09:00", 240, "2026-01-01", "2026-03-31", null));

        AvailabilityRuleRequest secondRange =
                new AvailabilityRuleRequest(
                        MONDAY_DOW, "09:00", 240, "2026-04-01", "2026-06-30", null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), secondRange);

        assertThat(created.effectiveFrom()).isEqualTo("2026-04-01");
        assertThat(rules.findByGuideId(guideAId)).hasSize(2);
    }

    @Test
    void createRule_rejectsSameTimeOfDay_withOverlappingEffectiveRanges() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        MONDAY_DOW, "09:00", 240, "2026-01-01", "2026-03-31", null));

        AvailabilityRuleRequest overlappingRange =
                new AvailabilityRuleRequest(
                        MONDAY_DOW, "09:00", 240, "2026-03-15", "2026-06-30", null);

        assertThatThrownBy(
                        () -> writeService.createRule(actingUser(guideAUserId), overlappingRange))
                .isInstanceOf(ValidationException.class);
        assertThat(rules.findByGuideId(guideAId)).hasSize(1);
    }

    @Test
    void createRule_rejectsSameTimeOfDay_withBothOpenEndedEffectiveRanges() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 240, "2026-01-01", null, null));

        AvailabilityRuleRequest secondOpenEnded =
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 240, "2026-06-01", null, null);

        assertThatThrownBy(() -> writeService.createRule(actingUser(guideAUserId), secondOpenEnded))
                .isInstanceOf(ValidationException.class);
        assertThat(rules.findByGuideId(guideAId)).hasSize(1);
    }

    @Test
    void updateRule_movingIntoAdjacency_coalesces() {
        // CTL-54 coalesce: this used to 422 (moving into an overlap with a same-effective
        // sibling); moving a rule's span into adjacency/overlap with a same-effective ACTIVE
        // sibling now MERGES them into one rule instead of being rejected.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, null));
        AvailabilityRuleResponse second =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(MONDAY_DOW, "12:00", 60, null, null, null));

        // Move the second rule (12:00-13:00) to 10:00-11:00, touching the first (09:00-10:00).
        AvailabilityRuleRequest movedIntoAdjacency =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 60, null, null, null);
        AvailabilityRuleResponse updated =
                writeService.updateRule(
                        actingUser(guideAUserId), UUID.fromString(second.id()), movedIntoAdjacency);

        assertThat(updated.startLocal()).isEqualTo("09:00");
        assertThat(updated.windowMin()).isEqualTo(120);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) MONDAY_DOW);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.get(0).getWindowMin()).isEqualTo(120);
    }

    @Test
    void updateRule_allowsKeepingOwnTimes_selfExcludedFromOverlapCheck() {
        AvailabilityRuleResponse created =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, null));

        AvailabilityRuleRequest sameTimes =
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, false);
        AvailabilityRuleResponse updated =
                writeService.updateRule(
                        actingUser(guideAUserId), UUID.fromString(created.id()), sameTimes);

        assertThat(updated.startLocal()).isEqualTo("09:00");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void updateRule_partialActiveOnly_preservesOtherFieldsAndFlipsActive() {
        AvailabilityRuleResponse created =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(0, "09:00", 60, null, null, true));
        UUID ruleId = UUID.fromString(created.id());

        // Partial PATCH: ONLY active is set -- dayOfWeek/startLocal/windowMin/effectiveFrom/
        // effectiveTo are all null, exactly what the weekly Available/Unavailable toggle sends.
        AvailabilityRuleRequest activeOnly =
                new AvailabilityRuleRequest(null, null, null, null, null, false);

        AvailabilityRuleResponse updated =
                writeService.updateRule(actingUser(guideAUserId), ruleId, activeOnly);

        assertThat(updated.active()).isFalse();
        assertThat(updated.dayOfWeek()).isEqualTo(0);
        assertThat(updated.startLocal()).isEqualTo("09:00");
        assertThat(updated.windowMin()).isEqualTo(60);

        // Persisted, not just the returned response.
        GuideAvailabilityRuleEntity stored = rules.findById(ruleId).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getDayOfWeek()).isEqualTo((short) 0);
        assertThat(stored.getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.getWindowMin()).isEqualTo(60);
    }

    @Test
    void updateRule_partialReactivate_resolvesStoredFieldsAndCoalesces() {
        // B is created first (active, no sibling yet) then deactivated, so an identical-span A
        // can later be created active (validateNoOverlap only rejects against ACTIVE siblings).
        AvailabilityRuleResponse b =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, true));
        UUID bId = UUID.fromString(b.id());
        writeService.updateRule(
                actingUser(guideAUserId),
                bId,
                new AvailabilityRuleRequest(null, null, null, null, null, false));

        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, null));

        // Partial PATCH: ONLY active=true (re-activate B) -- must still resolve B's stored
        // dayOfWeek/startLocal/windowMin/effectiveFrom/effectiveTo. B and A land in the SAME
        // coalesce group (same day + same default effective range) with an identical span, so
        // CTL-54 coalesce now MERGES them into one active rule instead of the old 422.
        AvailabilityRuleRequest reactivateOnly =
                new AvailabilityRuleRequest(null, null, null, null, null, true);

        AvailabilityRuleResponse updated =
                writeService.updateRule(actingUser(guideAUserId), bId, reactivateOnly);

        assertThat(updated.active()).isTrue();
        assertThat(updated.startLocal()).isEqualTo("09:00");
        assertThat(updated.windowMin()).isEqualTo(60);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) MONDAY_DOW);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.get(0).getWindowMin()).isEqualTo(60);
    }

    @Test
    void updateRule_providedInvalidDayOfWeek_stillRejected() {
        AvailabilityRuleResponse created =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null));
        UUID ruleId = UUID.fromString(created.id());

        AvailabilityRuleRequest invalidDayOfWeek =
                new AvailabilityRuleRequest(7, null, null, null, null, null);

        assertThatThrownBy(
                        () ->
                                writeService.updateRule(
                                        actingUser(guideAUserId), ruleId, invalidDayOfWeek))
                .isInstanceOf(ValidationException.class)
                .hasMessage("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
    }

    // ---------------------------------------------------------------------
    // CTL-54 — write-time coalesce: contiguous/overlapping same-effective-range ACTIVE weekly
    // rules merge into one stored rule instead of being rejected as an overlap.
    // ---------------------------------------------------------------------

    @Test
    void createRule_coalescesTouchingRanges() {
        // The user-reported case: Sunday 9:00-10:00 + 10:00-10:45 -> ONE rule, 9:00-10:45.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(0, "09:00", 60, null, null, null));

        AvailabilityRuleResponse merged =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(0, "10:00", 45, null, null, null));

        assertThat(merged.startLocal()).isEqualTo("09:00");
        assertThat(merged.windowMin()).isEqualTo(105);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) 0);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.get(0).getWindowMin()).isEqualTo(105);

        // Every weekly-recurring materialized occurrence is one contiguous 105-minute block, not
        // two separate 60/45-minute ones.
        List<GuideAvailabilityOccurrenceEntity> occ =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(occ).isNotEmpty();
        assertThat(occ)
                .allSatisfy(
                        o ->
                                assertThat(
                                                java.time.Duration.between(
                                                                o.getDuringStartAt(),
                                                                o.getDuringEndAt())
                                                        .toMinutes())
                                        .isEqualTo(105));
    }

    @Test
    void createRule_coalescesOverlappingRanges() {
        // 09:00-10:30 + 10:00-11:00 overlap (previously the 2nd create would 422) -> ONE rule,
        // 09:00-11:00.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(0, "09:00", 90, null, null, null));

        AvailabilityRuleResponse merged =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(0, "10:00", 60, null, null, null));

        assertThat(merged.startLocal()).isEqualTo("09:00");
        assertThat(merged.windowMin()).isEqualTo(120);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) 0);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getWindowMin()).isEqualTo(120);
    }

    @Test
    void createRule_bridgingRangeUnionsWholeRun() {
        // 09:00-10:00 and 11:00-12:00 (a 1-hour gap -- disjoint, no merge yet) then a bridging
        // 10:00-11:00 unions the whole run into ONE rule, 09:00-12:00.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(0, "09:00", 60, null, null, null));
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(0, "11:00", 60, null, null, null));
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) 0)).hasSize(2);

        AvailabilityRuleResponse merged =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(0, "10:00", 60, null, null, null));

        assertThat(merged.startLocal()).isEqualTo("09:00");
        assertThat(merged.windowMin()).isEqualTo(180);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) 0);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
        assertThat(stored.get(0).getWindowMin()).isEqualTo(180);
    }

    @Test
    void createRule_differentEffectiveFrom_notMerged() {
        // Same dayOfWeek/touching times, but the second create runs against a clock advanced by
        // >= 1 day -- a different default effectiveFrom means a DIFFERENT coalesce group, so the
        // two rules stay separate (not merged, and not rejected -- touching bounds never overlap).
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "09:00", 60, null, null, null));

        Clock nextDayClock = Clock.offset(FIXED_CLOCK, java.time.Duration.ofDays(1));
        AvailabilityService nextDayAvailabilityService =
                new AvailabilityService(
                        rules,
                        exceptions,
                        occurrences,
                        dstNotices,
                        settingsRepo,
                        entityManager,
                        nextDayClock);
        AvailabilityWriteService nextDayWriteService =
                new AvailabilityWriteService(
                        rules,
                        exceptions,
                        settingsRepo,
                        guides,
                        nextDayAvailabilityService,
                        entityManager,
                        bookings,
                        occurrences,
                        nextDayClock);

        AvailabilityRuleResponse second =
                nextDayWriteService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 60, null, null, null));

        assertThat(second.effectiveFrom()).isNotEqualTo(FIXED_TODAY.toString());

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) MONDAY_DOW);
        assertThat(stored).hasSize(2);
    }

    @Test
    void createRule_inactiveSibling_notMerged() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(0, "10:00", 60, null, null, null));

        AvailabilityRuleResponse created =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(0, "09:00", 60, null, null, false));

        assertThat(created.active()).isFalse();

        List<GuideAvailabilityRuleEntity> stored = rules.findByGuideId(guideAId);
        assertThat(stored).hasSize(2);
        assertThat(stored)
                .extracting(GuideAvailabilityRuleEntity::isActive)
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    void coalesce_unionStaysWithinDay() {
        // 22:00-23:00 + 23:00-24:00 touch exactly at end-of-day -> ONE rule, 22:00-24:00 (1440
        // minutes total, the allowed exactly-midnight-ending boundary).
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(MONDAY_DOW, "22:00", 60, null, null, null));

        AvailabilityRuleResponse merged =
                writeService.createRule(
                        actingUser(guideAUserId),
                        new AvailabilityRuleRequest(MONDAY_DOW, "23:00", 60, null, null, null));

        assertThat(merged.startLocal()).isEqualTo("22:00");
        assertThat(merged.windowMin()).isEqualTo(120);

        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) MONDAY_DOW);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getWindowMin()).isEqualTo(120);
    }

    // ---------------------------------------------------------------------
    // Task 3 (CTL-54 v2.1) — date-specific newest-wins trim/replace, stored non-overlapping.
    // ---------------------------------------------------------------------

    @Test
    void createException_trimsPriorAdditional_whenNewUnavailableOverlaps() {
        LocalDate d = FIXED_TODAY;
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "ADDITIONAL", "09:00", 60, null));

        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:30", 90, null));

        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored)
                .extracting(
                        AvailabilityExceptionEntity::getKind,
                        e -> e.getStartLocal().toString(),
                        AvailabilityExceptionEntity::getWindowMin)
                .containsExactlyInAnyOrder(
                        tuple(AvailabilityExceptionKind.ADDITIONAL, "09:00", 30),
                        tuple(AvailabilityExceptionKind.UNAVAILABLE, "09:30", 90));

        // Stored same-date exceptions are non-overlapping.
        IntervalMath.Span spanA =
                IntervalMath.spanOf(stored.get(0).getStartLocal(), stored.get(0).getWindowMin());
        IntervalMath.Span spanB =
                IntervalMath.spanOf(stored.get(1).getStartLocal(), stored.get(1).getWindowMin());
        assertThat(IntervalMath.overlaps(spanA, spanB)).isFalse();

        // Resolved net: 09:00-09:30 available; 09:30-11:00 is a BLOCK, i.e. no positive occurrence.
        List<GuideAvailabilityOccurrenceEntity> occ =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(occ).hasSize(1);
        assertThat(occ.get(0).getDuringStartAt())
                .isEqualTo(d.atTime(9, 0).atZone(LA_ZONE).toInstant());
        assertThat(occ.get(0).getDuringEndAt())
                .isEqualTo(d.atTime(9, 30).atZone(LA_ZONE).toInstant());
    }

    @Test
    void createException_trimsPriorUnavailable_whenNewAdditionalOverlaps_symmetric() {
        LocalDate d = FIXED_TODAY;
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:30", 90, null));

        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "ADDITIONAL", "09:00", 60, null));

        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored)
                .extracting(
                        AvailabilityExceptionEntity::getKind,
                        e -> e.getStartLocal().toString(),
                        AvailabilityExceptionEntity::getWindowMin)
                .containsExactlyInAnyOrder(
                        tuple(AvailabilityExceptionKind.UNAVAILABLE, "10:00", 60),
                        tuple(AvailabilityExceptionKind.ADDITIONAL, "09:00", 60));

        // The ADDITIONAL wins on 09:00-10:00.
        List<GuideAvailabilityOccurrenceEntity> occ =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(occ).hasSize(1);
        assertThat(occ.get(0).getDuringStartAt())
                .isEqualTo(d.atTime(9, 0).atZone(LA_ZONE).toInstant());
        assertThat(occ.get(0).getDuringEndAt())
                .isEqualTo(d.atTime(10, 0).atZone(LA_ZONE).toInstant());
    }

    @Test
    void createException_multiDay_appliesPerDate_andTrimsEachDatesOverlaps() {
        LocalDate d0 = FIXED_TODAY;
        LocalDate d1 = d0.plusDays(1);
        LocalDate d2 = d0.plusDays(2);

        // A prior ADDITIONAL on the middle day that the all-day block should fully cover (and
        // therefore fully trim away -- an empty remnant, per IntervalMath.subtract).
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d1.toString(), "ADDITIONAL", "09:00", 60, null));

        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(
                        null,
                        "UNAVAILABLE",
                        "00:00",
                        1440,
                        "closed",
                        d0.toString(),
                        d2.toString()));

        for (LocalDate d : List.of(d0, d1, d2)) {
            List<AvailabilityExceptionEntity> stored =
                    exceptions.findByGuideIdAndExceptionDate(guideAId, d);
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).getKind()).isEqualTo(AvailabilityExceptionKind.UNAVAILABLE);
            assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("00:00");
            assertThat(stored.get(0).getWindowMin()).isEqualTo(1440);
        }
        // Fully blocked on all three dates -> no resolved occurrences.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isEmpty();
    }

    @Test
    void createException_leavesNonOverlappingPriorException_physicallyUntouched() {
        LocalDate d = FIXED_TODAY;
        var untouched =
                writeService.createException(
                        actingUser(guideAUserId),
                        new AvailabilityExceptionRequest(
                                d.toString(), "ADDITIONAL", "14:00", 60, "afternoon"));

        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:30", 90, null));

        assertThat(exceptions.findByGuideIdAndExceptionDate(guideAId, d)).hasSize(2);

        // Left PHYSICALLY untouched: same row (same id), same reason, unchanged fields -- never
        // deleted/reinserted just because a sibling on the same date changed.
        AvailabilityExceptionEntity untouchedRow =
                exceptions.findById(UUID.fromString(untouched.id())).orElseThrow();
        assertThat(untouchedRow.getKind()).isEqualTo(AvailabilityExceptionKind.ADDITIONAL);
        assertThat(untouchedRow.getStartLocal().toString()).isEqualTo("14:00");
        assertThat(untouchedRow.getWindowMin()).isEqualTo(60);
        assertThat(untouchedRow.getReason()).isEqualTo("afternoon");
    }

    @Test
    void createException_trim_leavesBookingsTableUntouched() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 240, null, null, null);
        writeService.createRule(actingUser(guideAUserId), ruleReq);

        Instant scheduledStart = NEXT_MONDAY.atTime(12, 0).atZone(LA_ZONE).toInstant();
        Instant scheduledEnd = NEXT_MONDAY.atTime(13, 0).atZone(LA_ZONE).toInstant();
        BookingEntity booking =
                bookings.saveAndFlush(
                        confirmedBooking(guideAId, offeringId, scheduledStart, scheduledEnd));

        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(
                        NEXT_MONDAY.toString(), "ADDITIONAL", "09:00", 60, null));
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(
                        NEXT_MONDAY.toString(), "UNAVAILABLE", "09:30", 90, null));

        BookingEntity reloaded = bookings.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getScheduledStartAt()).isEqualTo(scheduledStart);
        assertThat(reloaded.getScheduledEndAt()).isEqualTo(scheduledEnd);
    }

    @Test
    void createException_rejectsRangeOver366InclusiveDates() {
        LocalDate from = FIXED_TODAY;
        // Difference of 366 days -> 367 inclusive dates, one over the 366 inclusive-date cap.
        LocalDate to = from.plusDays(366);
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        null, "UNAVAILABLE", "09:00", 60, null, from.toString(), to.toString());

        assertThatThrownBy(() -> writeService.createException(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(exceptions.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createException_accepts366InclusiveDates() {
        LocalDate from = FIXED_TODAY;
        // Difference of 365 days -> exactly 366 inclusive dates, right at the cap.
        LocalDate to = from.plusDays(365);
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        null, "UNAVAILABLE", "00:00", 60, null, from.toString(), to.toString());

        writeService.createException(actingUser(guideAUserId), req);

        // Inclusive [from, to] -> 366 dates, one exception row per date.
        assertThat(exceptions.findByGuideId(guideAId)).hasSize(366);
    }

    @Test
    void createException_multiDayExpandsAcross229InLeapYear() {
        // 2028 is a leap year -- Feb has 29 days. This pins that the multi-day expansion loop
        // (dateFrom.plusDays(i), a LocalDate) is leap-aware and does not skip/mis-land on 2/29.
        LocalDate d0 = LocalDate.of(2028, 2, 28);
        LocalDate d1 = LocalDate.of(2028, 2, 29);
        LocalDate d2 = LocalDate.of(2028, 3, 1);

        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        null, "UNAVAILABLE", "00:00", 1440, "closed", d0.toString(), d2.toString());

        writeService.createException(actingUser(guideAUserId), req);

        List<AvailabilityExceptionEntity> stored = exceptions.findByGuideId(guideAId);
        assertThat(stored).hasSize(3);
        assertThat(stored)
                .extracting(AvailabilityExceptionEntity::getExceptionDate)
                .containsExactlyInAnyOrder(d0, d1, d2);
    }

    @Test
    void createException_rejectsNonExistentLeapDate() {
        // 2027 is NOT a leap year -- Feb 29 does not exist. Must be a clean 422
        // (ValidationException
        // via parseLocalDate's DateTimeParseException catch), never a raw 500.
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest("2027-02-29", "UNAVAILABLE", "09:00", 60, null);

        assertThatThrownBy(() -> writeService.createException(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(exceptions.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createException_rejectsOverrideCrossingMidnight() {
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "UNAVAILABLE", "22:00", 240, null);

        assertThatThrownBy(() -> writeService.createException(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(exceptions.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createException_rejectsSubMinuteStartLocal() {
        // "23:30:40" + windowMin=30: the same-day guard truncates to the minute (23:30 + 30 =
        // 24:00, OK), but materialization keeps full precision and would roll to 00:00:40 the NEXT
        // day (CTL-54 #7). Sub-minute precision must be rejected so validation and materialization
        // agree.
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "UNAVAILABLE", "23:30:40", 30, null);

        assertThatThrownBy(() -> writeService.createException(actingUser(guideAUserId), req))
                .isInstanceOf(ValidationException.class);
        assertThat(exceptions.findByGuideId(guideAId)).isEmpty();
    }

    @Test
    void createException_allowsOverrideEndingExactlyAtMidnight() {
        AvailabilityExceptionRequest req =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "UNAVAILABLE", "22:00", 120, null);

        var created = writeService.createException(actingUser(guideAUserId), req);

        assertThat(created.startLocal()).isEqualTo("22:00");
        assertThat(exceptions.findByGuideId(guideAId)).hasSize(1);
    }

    @Test
    void createException_beyondHorizon_persistsButStaysInert() {
        LocalDate farFuture = FIXED_TODAY.plusDays(400); // horizon is HORIZON_DAYS=375.
        assertThat(farFuture).isAfter(FIXED_TODAY.plusDays(AvailabilityService.HORIZON_DAYS));

        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        writeService.createRule(actingUser(guideAUserId), ruleReq);
        int occurrencesBefore = occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId).size();

        var created =
                writeService.createException(
                        actingUser(guideAUserId),
                        new AvailabilityExceptionRequest(
                                farFuture.toString(), "UNAVAILABLE", "09:00", 60, null));

        assertThat(created.exceptionDate()).isEqualTo(farFuture.toString());
        assertThat(exceptions.findByGuideIdAndExceptionDate(guideAId, farFuture)).hasSize(1);
        // Inert -- occurrences within the horizon are unchanged (the far-future date is outside the
        // materialization window, so the projection never evaluates it).
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId))
                .hasSize(occurrencesBefore);
    }

    @Test
    void updateException_selfExcludesEditedRow_whileTrimmingSiblings() {
        LocalDate d = FIXED_TODAY;
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "ADDITIONAL", "14:00", 60, null));
        var edited =
                writeService.createException(
                        actingUser(guideAUserId),
                        new AvailabilityExceptionRequest(
                                d.toString(), "UNAVAILABLE", "09:00", 60, "orig"));

        // Shift the edited exception's OWN span forward by 30 min (09:00-10:00 -> 09:30-10:30). If
        // self-exclusion were broken, the pre-edit row would be treated as a SIBLING overlapping
        // its
        // own new span and leave a spurious [09:00,09:30) UNAVAILABLE remnant of itself behind.
        AvailabilityExceptionRequest updateReq =
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:30", 60, "moved");
        var updated =
                writeService.updateException(
                        actingUser(guideAUserId), UUID.fromString(edited.id()), updateReq);

        assertThat(updated.startLocal()).isEqualTo("09:30");
        assertThat(updated.reason()).isEqualTo("moved");

        // Exactly the untouched sibling + the cleanly-moved row -- NO spurious self-trim remnant.
        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored)
                .extracting(
                        AvailabilityExceptionEntity::getKind,
                        e -> e.getStartLocal().toString(),
                        AvailabilityExceptionEntity::getWindowMin)
                .containsExactlyInAnyOrder(
                        tuple(AvailabilityExceptionKind.ADDITIONAL, "14:00", 60),
                        tuple(AvailabilityExceptionKind.UNAVAILABLE, "09:30", 60));
    }

    // ---------------------------------------------------------------------
    // CTL-54 v2.1 remediation B2 — atomic single-day override replace.
    // ---------------------------------------------------------------------

    @Test
    void replaceOverrides_replacesSameKind_insertsWindows_leavesOtherKindUntouched() {
        LocalDate d = FIXED_TODAY;
        // A same-kind (UNAVAILABLE) row that must be REPLACED, and a non-overlapping other-kind
        // (ADDITIONAL) row that must be left PHYSICALLY untouched (same id + reason).
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:00", 60, null));
        var otherKind =
                writeService.createException(
                        actingUser(guideAUserId),
                        new AvailabilityExceptionRequest(
                                d.toString(), "ADDITIONAL", "14:00", 60, "afternoon"));

        OverrideReplaceRequest req =
                new OverrideReplaceRequest(
                        d.toString(),
                        "UNAVAILABLE",
                        List.of(new OverrideReplaceRequest.Window("10:00", 60)));

        List<com.CampusToursLive.web.dto.AvailabilityExceptionResponse> result =
                writeService.replaceOverrides(guideAId, req);

        // The prior UNAVAILABLE 09:00 is gone; the new UNAVAILABLE 10:00 is in; ADDITIONAL kept.
        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored)
                .extracting(
                        AvailabilityExceptionEntity::getKind,
                        e -> e.getStartLocal().toString(),
                        AvailabilityExceptionEntity::getWindowMin)
                .containsExactlyInAnyOrder(
                        tuple(AvailabilityExceptionKind.ADDITIONAL, "14:00", 60),
                        tuple(AvailabilityExceptionKind.UNAVAILABLE, "10:00", 60));

        // The returned payload mirrors the stored date set.
        assertThat(result)
                .extracting(
                        com.CampusToursLive.web.dto.AvailabilityExceptionResponse::kind,
                        com.CampusToursLive.web.dto.AvailabilityExceptionResponse::startLocal)
                .containsExactlyInAnyOrder(
                        tuple("ADDITIONAL", "14:00"), tuple("UNAVAILABLE", "10:00"));

        // The other-kind row was left physically untouched: same id + reason preserved.
        AvailabilityExceptionEntity untouched =
                exceptions.findById(UUID.fromString(otherKind.id())).orElseThrow();
        assertThat(untouched.getReason()).isEqualTo("afternoon");
        assertThat(untouched.getStartLocal().toString()).isEqualTo("14:00");
    }

    @Test
    void replaceOverrides_emptyWindows_clearsThatKindForTheDay_keepsOtherKind() {
        LocalDate d = FIXED_TODAY;
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:00", 60, null));
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "ADDITIONAL", "14:00", 60, null));

        // Empty windows == clear this kind for the day (allowed).
        OverrideReplaceRequest req =
                new OverrideReplaceRequest(d.toString(), "UNAVAILABLE", List.of());

        writeService.replaceOverrides(guideAId, req);

        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored)
                .extracting(AvailabilityExceptionEntity::getKind, e -> e.getStartLocal().toString())
                .containsExactly(tuple(AvailabilityExceptionKind.ADDITIONAL, "14:00"));
    }

    @Test
    void replaceOverrides_invalidWindow_rejectedBeforeAnyMutation() {
        // (c1) An invalid (cross-midnight) window must 422 at the entry guard, BEFORE any delete —
        // so the prior same-kind override is still fully intact because nothing was deleted yet.
        LocalDate d = FIXED_TODAY;
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:00", 60, null));

        OverrideReplaceRequest bad =
                new OverrideReplaceRequest(
                        d.toString(),
                        "UNAVAILABLE",
                        List.of(
                                new OverrideReplaceRequest.Window(
                                        "23:59", 120))); // crosses midnight

        assertThatThrownBy(() -> writeService.replaceOverrides(guideAId, bad))
                .isInstanceOf(ValidationException.class);

        // Untouched — nothing was deleted because validation ran first.
        List<AvailabilityExceptionEntity> stored =
                exceptions.findByGuideIdAndExceptionDate(guideAId, d);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getKind()).isEqualTo(AvailabilityExceptionKind.UNAVAILABLE);
        assertThat(stored.get(0).getStartLocal().toString()).isEqualTo("09:00");
    }

    // ---------------------------------------------------------------------
    // CTL-54 v2.1 remediation B2 — atomic weekly-rule replace (Task 5).
    // ---------------------------------------------------------------------

    @Test
    void replaceRules_replacesThatWeekday_leavesOtherWeekdaysUntouched() {
        short otherDow = (short) ((TODAY_DOW + 1) % 7);
        // A rule on TODAY_DOW that must be REPLACED, and one on a DIFFERENT weekday that must stay.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null));
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest((int) otherDow, "08:00", 60, null, null, null));

        RulesReplaceRequest req =
                new RulesReplaceRequest(
                        TODAY_DOW, List.of(new RulesReplaceRequest.Window("14:00", 60)));

        List<AvailabilityRuleResponse> result = writeService.replaceRules(guideAId, req);

        // TODAY_DOW now has exactly the new 14:00 window; the prior 09:00 is gone.
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) TODAY_DOW))
                .extracting(
                        r -> r.getStartLocal().toString(),
                        GuideAvailabilityRuleEntity::getWindowMin)
                .containsExactly(tuple("14:00", 60));
        // The OTHER weekday's rule is physically untouched.
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, otherDow))
                .extracting(
                        r -> r.getStartLocal().toString(),
                        GuideAvailabilityRuleEntity::getWindowMin)
                .containsExactly(tuple("08:00", 60));

        // The returned payload mirrors the weekday's resulting rules.
        assertThat(result)
                .extracting(
                        AvailabilityRuleResponse::dayOfWeek, AvailabilityRuleResponse::startLocal)
                .containsExactly(tuple((int) TODAY_DOW, "14:00"));

        // rematerialize ran in the same transaction -> the guide has occurrences again.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isNotEmpty();
    }

    @Test
    void replaceRules_emptyWindows_clearsThatWeekday_keepsOtherWeekdays() {
        short otherDow = (short) ((TODAY_DOW + 1) % 7);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null));
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest((int) otherDow, "08:00", 60, null, null, null));

        // Empty windows == clear this weekday's rules (allowed).
        RulesReplaceRequest req = new RulesReplaceRequest(TODAY_DOW, List.of());

        List<AvailabilityRuleResponse> result = writeService.replaceRules(guideAId, req);

        assertThat(result).isEmpty();
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) TODAY_DOW))
                .isEmpty();
        // The other weekday survives.
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, otherDow))
                .extracting(r -> r.getStartLocal().toString())
                .containsExactly("08:00");
    }

    @Test
    void replaceRules_invalidWindow_rejectedBeforeAnyMutation() {
        // (c1) An invalid (cross-midnight) window must 422 at the entry guard, BEFORE any delete —
        // so the prior rule for that weekday is still fully intact because nothing was deleted.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null));

        RulesReplaceRequest bad =
                new RulesReplaceRequest(
                        TODAY_DOW,
                        List.of(new RulesReplaceRequest.Window("23:59", 120))); // crosses midnight

        assertThatThrownBy(() -> writeService.replaceRules(guideAId, bad))
                .isInstanceOf(ValidationException.class);

        // Untouched — nothing was deleted because validation ran first.
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) TODAY_DOW))
                .extracting(r -> r.getStartLocal().toString())
                .containsExactly("09:00");
    }

    @Test
    void replaceRules_overlappingWindows_coalescedIntoOneRule() {
        // CTL-54 accept-and-resolve: two self-overlapping request windows no longer 422 — they
        // MERGE into one stored rule, exactly like createRule coalesces a same-group overlap. The
        // occurrence layer collapses [09:00-11:00, 10:00-12:00] and [09:00-12:00] to identical net
        // availability anyway, so rejecting bought no correctness — only friction with a FE modal
        // that has no cross-row overlap guard.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "07:00", 60, null, null, null));

        RulesReplaceRequest overlapping =
                new RulesReplaceRequest(
                        TODAY_DOW,
                        List.of(
                                new RulesReplaceRequest.Window("09:00", 120), // 09:00-11:00
                                new RulesReplaceRequest.Window(
                                        "10:00", 120))); // 10:00-12:00 overlaps

        List<AvailabilityRuleResponse> result = writeService.replaceRules(guideAId, overlapping);

        // The prior 07:00 rule is gone (replaced); the two overlapping windows merged into a single
        // 09:00-12:00 (180 min) rule.
        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) TODAY_DOW))
                .extracting(
                        r -> r.getStartLocal().toString(),
                        GuideAvailabilityRuleEntity::getWindowMin)
                .containsExactly(tuple("09:00", 180));
        assertThat(result)
                .extracting(
                        AvailabilityRuleResponse::startLocal, AvailabilityRuleResponse::windowMin)
                .containsExactly(tuple("09:00", 180));
    }

    @Test
    void replaceRules_touchingWindows_coalescedIntoOneRule() {
        // CTL-54 accept-and-resolve: touching windows (09:00-10:00 + 10:00-11:00) merge into one
        // 09:00-11:00 rule instead of being stored as two abutting rows — matching createRule's
        // coalesce, which merges overlap AND touch, and the occurrence projection layer.
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "07:00", 60, null, null, null));

        RulesReplaceRequest touching =
                new RulesReplaceRequest(
                        TODAY_DOW,
                        List.of(
                                new RulesReplaceRequest.Window("09:00", 60), // 09:00-10:00
                                new RulesReplaceRequest.Window(
                                        "10:00", 60))); // 10:00-11:00 touches

        List<AvailabilityRuleResponse> result = writeService.replaceRules(guideAId, touching);

        assertThat(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideAId, (short) TODAY_DOW))
                .extracting(
                        r -> r.getStartLocal().toString(),
                        GuideAvailabilityRuleEntity::getWindowMin)
                .containsExactly(tuple("09:00", 120));
        assertThat(result)
                .extracting(
                        AvailabilityRuleResponse::startLocal, AvailabilityRuleResponse::windowMin)
                .containsExactly(tuple("09:00", 120));
    }

    // ---------------------------------------------------------------------
    // Settings — auto-provision + tz cascade + reproject.
    // ---------------------------------------------------------------------

    @Test
    void getSettings_autoProvisionsDefaults_whenNoRowExists() {
        assertThat(settingsRepo.findByGuideId(guideAId)).isEmpty();

        GuideBookingSettingsResponse settings = writeService.getSettings(actingUser(guideAUserId));

        assertThat(settings.guideId()).isEqualTo(guideAId.toString());
        assertThat(settings.acceptanceMode()).isEqualTo("MANUAL");
        assertThat(settings.responseDeadlineMin()).isEqualTo(90);
        assertThat(settings.minNoticeMin()).isEqualTo(1440);
        assertThat(settings.maxAdvanceDays()).isEqualTo(30);
        assertThat(settings.bufferBeforeMin()).isEqualTo(0);
        assertThat(settings.bufferAfterMin()).isEqualTo(15);
        assertThat(settings.durationsOffered()).containsExactly(30, 45, 60, 90);
        assertThat(settings.timezone()).isEqualTo(AvailabilityService.DEFAULT_TIMEZONE);
        assertThat(settings.updatedAt()).isNotNull();
        assertThat(settingsRepo.findByGuideId(guideAId)).isPresent();
    }

    @Test
    void updateSettings_cascadesNewTimezoneToRules_andReprojectsOccurrences() {
        // A rule that only ever fires on FIXED_TODAY, at 09:00 local. In July, LA is PDT (-07:00)
        // and New York is EDT (-04:00) -- a 3-hour difference lets the test observe the reproject.
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(
                        TODAY_DOW,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.toString(),
                        null);
        writeService.createRule(actingUser(guideAUserId), ruleReq);

        List<GuideAvailabilityOccurrenceEntity> beforeOccurrences =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(beforeOccurrences).hasSize(1);
        Instant beforeStart = beforeOccurrences.get(0).getDuringStartAt();
        assertThat(beforeStart).isEqualTo(Instant.parse("2026-07-11T16:00:00Z")); // 09:00 PDT

        GuideBookingSettingsUpdateRequest settingsReq =
                new GuideBookingSettingsUpdateRequest(
                        null, null, null, null, null, null, null, "America/New_York");
        writeService.updateSettings(actingUser(guideAUserId), settingsReq);

        // Cascade: the existing rule's own timezone column now matches the new settings tz.
        List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideAId);
        assertThat(guideRules)
                .extracting(GuideAvailabilityRuleEntity::getTimezone)
                .containsExactly("America/New_York");

        // Reproject: the same wall-clock 09:00 now resolves 3 hours earlier in UTC (EDT = -04:00).
        List<GuideAvailabilityOccurrenceEntity> afterOccurrences =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(afterOccurrences).hasSize(1);
        assertThat(afterOccurrences.get(0).getDuringStartAt())
                .isEqualTo(Instant.parse("2026-07-11T13:00:00Z")) // 09:00 EDT
                .isNotEqualTo(beforeStart);
    }

    @Test
    void updateSettings_doesNotCascade_whenTimezoneUnchanged() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        writeService.createRule(actingUser(guideAUserId), ruleReq);

        GuideBookingSettingsUpdateRequest settingsReq =
                new GuideBookingSettingsUpdateRequest(
                        null, 120, null, null, null, null, null, null);
        GuideBookingSettingsResponse updated =
                writeService.updateSettings(actingUser(guideAUserId), settingsReq);

        assertThat(updated.responseDeadlineMin()).isEqualTo(120);
        assertThat(updated.timezone()).isEqualTo(AvailabilityService.DEFAULT_TIMEZONE);
        assertThat(rules.findByGuideId(guideAId))
                .extracting(GuideAvailabilityRuleEntity::getTimezone)
                .containsExactly(AvailabilityService.DEFAULT_TIMEZONE);
    }

    @Test
    void updateSettings_roundTripsDurationsOffered_viaJsonbConverter() {
        GuideBookingSettingsUpdateRequest req =
                new GuideBookingSettingsUpdateRequest(
                        null, null, null, null, null, null, List.of(20, 40), null);

        writeService.updateSettings(actingUser(guideAUserId), req);

        GuideBookingSettingsEntity reloaded = settingsRepo.findByGuideId(guideAId).orElseThrow();
        assertThat(reloaded.getDurationsOffered()).containsExactly(20, 40);
    }

    @Test
    void updateSettings_appliesEveryField_inOneRequest() {
        GuideBookingSettingsUpdateRequest req =
                new GuideBookingSettingsUpdateRequest(
                        "AUTO", 45, 720, 60, 10, 20, List.of(30, 60), null);

        GuideBookingSettingsResponse updated =
                writeService.updateSettings(actingUser(guideAUserId), req);

        assertThat(updated.acceptanceMode()).isEqualTo("AUTO");
        assertThat(updated.responseDeadlineMin()).isEqualTo(45);
        assertThat(updated.minNoticeMin()).isEqualTo(720);
        assertThat(updated.maxAdvanceDays()).isEqualTo(60);
        assertThat(updated.bufferBeforeMin()).isEqualTo(10);
        assertThat(updated.bufferAfterMin()).isEqualTo(20);
        assertThat(updated.durationsOffered()).containsExactly(30, 60);
    }

    @Test
    void updateSettings_rejectsEachInvalidField() {
        UserEntity actor = actingUser(guideAUserId);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                "NOT_A_MODE",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, 0, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, -1, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, null, 0, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, null, 400, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, null, null, -1, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, null, null, null, -1, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null, null, null, null, null, null, List.of(),
                                                null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                writeService.updateSettings(
                                        actor,
                                        new GuideBookingSettingsUpdateRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of(30, -5),
                                                null)))
                .isInstanceOf(ValidationException.class);
    }

    // ---------------------------------------------------------------------
    // Rules/exceptions — happy-path update/delete + list.
    // ---------------------------------------------------------------------

    @Test
    void updateRule_appliesChangesAndRematerializes_whenOwned() {
        AvailabilityRuleRequest createReq =
                new AvailabilityRuleRequest(
                        TODAY_DOW,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.toString(),
                        null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), createReq);
        UUID ruleId = UUID.fromString(created.id());

        LocalDate nextDay = FIXED_TODAY.plusDays(7);
        int nextDow = nextDay.getDayOfWeek().getValue() % 7;
        AvailabilityRuleRequest updateReq =
                new AvailabilityRuleRequest(
                        nextDow, "10:30", 45, nextDay.toString(), nextDay.toString(), false);

        AvailabilityRuleResponse updated =
                writeService.updateRule(actingUser(guideAUserId), ruleId, updateReq);

        assertThat(updated.dayOfWeek()).isEqualTo(nextDow);
        assertThat(updated.startLocal()).isEqualTo("10:30");
        assertThat(updated.windowMin()).isEqualTo(45);
        assertThat(updated.effectiveFrom()).isEqualTo(nextDay.toString());
        assertThat(updated.active()).isFalse();
        // Timezone was never in the request and must stay the server-set value.
        assertThat(updated.timezone()).isEqualTo(AvailabilityService.DEFAULT_TIMEZONE);
        // Reprojected: the rule is now inactive, so it produces no occurrence.
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isEmpty();
    }

    @Test
    void updateRule_rejectsMalformedStartLocal() {
        AvailabilityRuleRequest createReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), createReq);
        UUID ruleId = UUID.fromString(created.id());

        AvailabilityRuleRequest badReq =
                new AvailabilityRuleRequest(TODAY_DOW, "not-a-time", 60, null, null, null);
        assertThatThrownBy(() -> writeService.updateRule(actingUser(guideAUserId), ruleId, badReq))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateRule_rejectsEffectiveToBeforeEffectiveFrom() {
        AvailabilityRuleRequest createReq =
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null);
        AvailabilityRuleResponse created =
                writeService.createRule(actingUser(guideAUserId), createReq);
        UUID ruleId = UUID.fromString(created.id());

        AvailabilityRuleRequest badReq =
                new AvailabilityRuleRequest(
                        TODAY_DOW,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.minusDays(1).toString(),
                        null);
        assertThatThrownBy(() -> writeService.updateRule(actingUser(guideAUserId), ruleId, badReq))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateException_appliesChangesAndRematerializes_whenOwned() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());

        LocalDate otherDay = FIXED_TODAY.plusDays(1);
        AvailabilityExceptionRequest updateReq =
                new AvailabilityExceptionRequest(
                        otherDay.toString(), "UNAVAILABLE", "11:30", 30, "changed");

        var updated =
                writeService.updateException(actingUser(guideAUserId), exceptionId, updateReq);

        assertThat(updated.exceptionDate()).isEqualTo(otherDay.toString());
        assertThat(updated.kind()).isEqualTo("UNAVAILABLE");
        assertThat(updated.startLocal()).isEqualTo("11:30");
        assertThat(updated.windowMin()).isEqualTo(30);
        assertThat(updated.reason()).isEqualTo("changed");
    }

    @Test
    void updateException_rejectsMalformedExceptionDate() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());

        AvailabilityExceptionRequest badReq =
                new AvailabilityExceptionRequest("not-a-date", "ADDITIONAL", "10:00", 60, null);
        assertThatThrownBy(
                        () ->
                                writeService.updateException(
                                        actingUser(guideAUserId), exceptionId, badReq))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateException_rejectsUnrecognizedKind() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());

        AvailabilityExceptionRequest badReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "NOT_A_KIND", "10:00", 60, null);
        assertThatThrownBy(
                        () ->
                                writeService.updateException(
                                        actingUser(guideAUserId), exceptionId, badReq))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deleteException_removesAndRematerializes_whenOwned() {
        AvailabilityExceptionRequest createReq =
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null);
        var created = writeService.createException(actingUser(guideAUserId), createReq);
        UUID exceptionId = UUID.fromString(created.id());
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isNotEmpty();

        var remaining = writeService.deleteException(actingUser(guideAUserId), exceptionId);

        assertThat(remaining).isEmpty();
        assertThat(exceptions.findByGuideId(guideAId)).isEmpty();
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId)).isEmpty();
    }

    @Test
    void listRules_returnsOnlyTheCallersOwnRules() {
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(TODAY_DOW, "09:00", 60, null, null, null));
        writeService.createRule(
                actingUserForGuideProfile(guideBId),
                new AvailabilityRuleRequest(TODAY_DOW, "08:00", 30, null, null, null));

        List<AvailabilityRuleResponse> guideARules =
                writeService.listRules(actingUser(guideAUserId));

        assertThat(guideARules).hasSize(1);
        assertThat(guideARules.get(0).startLocal()).isEqualTo("09:00");
    }

    @Test
    void listExceptions_returnsOnlyTheCallersOwnExceptions() {
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "ADDITIONAL", "10:00", 60, null));
        writeService.createException(
                actingUserForGuideProfile(guideBId),
                new AvailabilityExceptionRequest(
                        FIXED_TODAY.toString(), "UNAVAILABLE", "08:00", 30, null));

        var guideAExceptions = writeService.listExceptions(actingUser(guideAUserId));

        assertThat(guideAExceptions).hasSize(1);
        assertThat(guideAExceptions.get(0).kind()).isEqualTo("ADDITIONAL");
    }

    // ---------------------------------------------------------------------
    // Task 7 — "(A) allow + notify": an availability edit that uncovers an existing future
    // CONFIRMED booking still succeeds, but the write reports the affected booking(s).
    // ---------------------------------------------------------------------

    /** Next Monday after FIXED_TODAY (a Saturday) -- 2026-07-13. */
    private static final LocalDate NEXT_MONDAY = FIXED_TODAY.plusDays(2);

    private static final int MONDAY_DOW = NEXT_MONDAY.getDayOfWeek().getValue() % 7;

    @Test
    void deletingTheCoveringRule_succeeds_leavesBookingUntouched_andReportsIt() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 240, null, null, null);
        AvailabilityRuleResponse rule = writeService.createRule(actingUser(guideAUserId), ruleReq);

        Instant scheduledStart = NEXT_MONDAY.atTime(12, 0).atZone(LA_ZONE).toInstant();
        Instant scheduledEnd = NEXT_MONDAY.atTime(13, 0).atZone(LA_ZONE).toInstant();
        BookingEntity booking =
                bookings.saveAndFlush(
                        confirmedBooking(guideAId, offeringId, scheduledStart, scheduledEnd));
        // Contained BEFORE the edit — sanity check the fixture actually covers the booking.
        assertThat(occurrences.existsContaining(guideAId, scheduledStart, scheduledEnd)).isTrue();

        List<AvailabilityRuleResponse> remaining =
                writeService.deleteRule(actingUser(guideAUserId), UUID.fromString(rule.id()));

        // The edit succeeded (no exception) and left no rules.
        assertThat(remaining).isEmpty();
        // No occurrence covers the booking anymore.
        assertThat(occurrences.existsContaining(guideAId, scheduledStart, scheduledEnd)).isFalse();

        // The booking row itself is completely untouched.
        BookingEntity reloaded = bookings.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getScheduledStartAt()).isEqualTo(scheduledStart);
        assertThat(reloaded.getScheduledEndAt()).isEqualTo(scheduledEnd);

        // The write surfaces the now-uncovered booking as a warning.
        List<AffectedBookingResponse> affected =
                writeService.findFutureBookingsOutsideAvailability(guideAId);
        assertThat(affected).hasSize(1);
        assertThat(affected.get(0).bookingId()).isEqualTo(booking.getId().toString());
        assertThat(affected.get(0).bookingNumber()).isEqualTo(booking.getBookingNumber());
        assertThat(affected.get(0).status()).isEqualTo("CONFIRMED");
        assertThat(affected.get(0).scheduledStartAt()).isEqualTo(scheduledStart.toString());
        assertThat(affected.get(0).scheduledEndAt()).isEqualTo(scheduledEnd.toString());

        // Also reachable through the user-facing overload the controller calls.
        assertThat(writeService.findAffectedBookings(actingUser(guideAUserId))).hasSize(1);
    }

    @Test
    void editThatLeavesBookingCovered_reportsNoAffectedBookings() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 240, null, null, null);
        writeService.createRule(actingUser(guideAUserId), ruleReq);

        Instant scheduledStart = NEXT_MONDAY.atTime(12, 0).atZone(LA_ZONE).toInstant();
        Instant scheduledEnd = NEXT_MONDAY.atTime(13, 0).atZone(LA_ZONE).toInstant();
        bookings.saveAndFlush(confirmedBooking(guideAId, offeringId, scheduledStart, scheduledEnd));

        // A settings-only change (unrelated to the rule) still triggers rematerialize, but the
        // rule -- and therefore the occurrence covering the booking -- is untouched.
        GuideBookingSettingsUpdateRequest settingsReq =
                new GuideBookingSettingsUpdateRequest(null, 45, null, null, null, null, null, null);
        writeService.updateSettings(actingUser(guideAUserId), settingsReq);

        assertThat(occurrences.existsContaining(guideAId, scheduledStart, scheduledEnd)).isTrue();
        assertThat(writeService.findFutureBookingsOutsideAvailability(guideAId)).isEmpty();
    }

    @Test
    void pastConfirmedBooking_uncovered_isNotReported() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 240, null, null, null);
        AvailabilityRuleResponse rule = writeService.createRule(actingUser(guideAUserId), ruleReq);

        // A booking scheduled BEFORE "now" (the fixed clock) that would be uncovered by deleting
        // the rule -- must never be reported (Task 7 scopes strictly to FUTURE bookings).
        Instant pastStart = FIXED_TODAY.minusDays(30).atTime(12, 0).atZone(LA_ZONE).toInstant();
        Instant pastEnd = FIXED_TODAY.minusDays(30).atTime(13, 0).atZone(LA_ZONE).toInstant();
        bookings.saveAndFlush(confirmedBooking(guideAId, offeringId, pastStart, pastEnd));

        writeService.deleteRule(actingUser(guideAUserId), UUID.fromString(rule.id()));

        assertThat(writeService.findFutureBookingsOutsideAvailability(guideAId)).isEmpty();
    }

    @Test
    void nonConfirmedBooking_uncovered_isNotReported() {
        AvailabilityRuleRequest ruleReq =
                new AvailabilityRuleRequest(MONDAY_DOW, "10:00", 240, null, null, null);
        AvailabilityRuleResponse rule = writeService.createRule(actingUser(guideAUserId), ruleReq);

        // A future booking that is NOT CONFIRMED (e.g. still waiting on the guide) -- Task 7
        // scopes to CONFIRMED only; PENDING re-validation belongs to CTL-46, not this warning.
        Instant scheduledStart = NEXT_MONDAY.atTime(12, 0).atZone(LA_ZONE).toInstant();
        Instant scheduledEnd = NEXT_MONDAY.atTime(13, 0).atZone(LA_ZONE).toInstant();
        BookingEntity pending =
                confirmedBooking(guideAId, offeringId, scheduledStart, scheduledEnd);
        pending.setStatus(BookingStatus.PENDING_GUIDE_ACCEPTANCE);
        bookings.saveAndFlush(pending);

        writeService.deleteRule(actingUser(guideAUserId), UUID.fromString(rule.id()));

        assertThat(writeService.findFutureBookingsOutsideAvailability(guideAId)).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private static final java.time.ZoneId LA_ZONE = java.time.ZoneId.of("America/Los_Angeles");

    private TourOfferingEntity seedOffering(UUID guideId) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guideId);
        o.setUniversityId(universityId);
        o.setTitle("Campus Walk");
        o.setSlug("campus-walk-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        o.setStatus(TourStatus.ACTIVE);
        return offerings.save(o);
    }

    /** A CONFIRMED 60-min booking for the given guide/offering/interval, seeded directly. */
    private BookingEntity confirmedBooking(
            UUID guideId, UUID offeringId, Instant scheduledStart, Instant scheduledEnd) {
        UserEntity participant = users.save(user("Pat Participant"));
        BookingEntity b = new BookingEntity();
        b.setId(UUID.randomUUID());
        b.setBookingNumber("BK-T7" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setParticipantUserId(participant.getId());
        b.setGuideId(guideId);
        b.setTourOfferingId(offeringId);
        b.setUniversityId(universityId);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAcceptanceModeSnap(AcceptanceMode.MANUAL);
        b.setScheduledStartAt(scheduledStart);
        b.setScheduledEndAt(scheduledEnd);
        b.setReservedStartAt(scheduledStart);
        b.setReservedEndAt(scheduledEnd.plusSeconds(15 * 60));
        b.setBasePriceCents(5000L);
        b.setTotalCents(5000L);
        b.setPlatformFeeCents(0L);
        b.setGuideAmountCents(5000L);
        b.setCurrency("USD");
        return b;
    }

    private GuideProfileEntity seedGuide(String displayName) {
        UserEntity guideUser = users.save(user(displayName));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setUniversityId(universityId);
        guide.setMajor("Computer Science");
        guide.setApplicationStatus(GuideApplicationStatus.APPROVED);
        return guides.save(guide);
    }

    private UserEntity actingUser(UUID userId) {
        UserEntity u = new UserEntity();
        u.setId(userId);
        return u;
    }

    private UserEntity actingUserForGuideProfile(UUID guideProfileId) {
        GuideProfileEntity guide = guides.findById(guideProfileId).orElseThrow();
        return actingUser(guide.getUserId());
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("wt-" + UUID.randomUUID());
        u.setEmail("wt-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }

    private static GuideBookingSettingsEntity settings(UUID guideId, String timezone) {
        GuideBookingSettingsEntity s = new GuideBookingSettingsEntity();
        s.setGuideId(guideId);
        s.setTimezone(timezone);
        return s;
    }
}
