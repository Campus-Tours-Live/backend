package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.booking.BookingRepository;
import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
import com.CampusToursLive.web.dto.ResolvedOccurrence;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
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
 * Persistence integration test for {@link AvailabilityReadService} (CTL-54 Task 5b) against a REAL
 * PostgreSQL (Testcontainers). Rules -- and the occurrences + DST notices they materialize -- are
 * created through {@link AvailabilityWriteService} / {@link AvailabilityService} (the real write +
 * projection path, T3/T5) with a FIXED clock, so what this read service serves is exactly what
 * production would have coalesced -- never hand-assembled. Mirrors {@code
 * AvailabilityWriteServiceIntegrationTest}.
 *
 * <p>{@link AvailabilityReadService} itself is read-only and takes no clock -- it only reads
 * whatever the write path already persisted.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityReadServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final String LA = "America/Los_Angeles";
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 7, 6);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final int DOW_A = FIXED_TODAY.getDayOfWeek().getValue() % 7;
    private static final int DOW_B = FIXED_TODAY.plusDays(2).getDayOfWeek().getValue() % 7;

    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private AvailabilityExceptionRepository exceptions;
    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideAvailabilityDstNoticeRepository dstNotices;
    @Autowired private GuideBookingSettingsRepository settingsRepo;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;
    @Autowired private BookingRepository bookings;
    @Autowired private EntityManager entityManager;

    private AvailabilityReadService readService;
    private UUID universityId;
    private UUID guideAUserId;
    private UUID guideAId;
    private UUID guideBUserId;

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
        GuideProfileEntity guideB = seedGuide("Guide B");
        guideBUserId = guideB.getUserId();

        readService =
                new AvailabilityReadService(rules, occurrences, dstNotices, guides, settingsRepo);
    }

    private AvailabilityWriteService writeServiceWithClock(Clock clock) {
        AvailabilityService availabilityService =
                new AvailabilityService(
                        rules,
                        exceptions,
                        occurrences,
                        dstNotices,
                        settingsRepo,
                        entityManager,
                        clock);
        return new AvailabilityWriteService(
                rules,
                exceptions,
                settingsRepo,
                guides,
                availabilityService,
                entityManager,
                bookings,
                occurrences,
                clock);
    }

    // ---------------------------------------------------------------------
    // Happy path.
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_returnsRulesAndCoalescedOccurrences_ascendingNoDuplication() {
        AvailabilityWriteService writeService = writeServiceWithClock(FIXED_CLOCK);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        DOW_A,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.plusDays(14).toString(),
                        null));
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        DOW_B,
                        "14:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.plusDays(14).toString(),
                        null));

        List<GuideAvailabilityOccurrenceEntity> persisted =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(persisted).hasSize(5);

        ResolvedAvailabilityResponse resolved =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(resolved.rules()).hasSize(2);
        assertThat(resolved.occurrences()).hasSize(5);
        // Exactly the persisted rows, in the same ascending order -- not re-coalesced/duplicated.
        assertThat(resolved.occurrences())
                .extracting(ResolvedOccurrence::startAt, ResolvedOccurrence::endAt)
                .containsExactlyElementsOf(
                        persisted.stream()
                                .map(o -> Tuple.tuple(o.getDuringStartAt(), o.getDuringEndAt()))
                                .toList());
        for (int i = 1; i < resolved.occurrences().size(); i++) {
            assertThat(resolved.occurrences().get(i).startAt())
                    .isAfterOrEqualTo(resolved.occurrences().get(i - 1).endAt());
        }
        assertThat(resolved.dstGapDays()).isEmpty();
    }

    @Test
    void getResolvedAvailability_surfacesDstGapDay_whenProjectionShiftedAWindow() {
        // Sunday 2026-03-08 02:30 America/Los_Angeles falls in the spring-forward gap, mirroring
        // AvailabilityServiceIntegrationTest#rematerialize_persistsDstNoticeForSpringForwardDay.
        LocalDate springForward = LocalDate.of(2026, 3, 8);
        Clock dstClock =
                Clock.fixed(
                        springForward.minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        ZoneOffset.UTC);
        AvailabilityWriteService writeService = writeServiceWithClock(dstClock);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        0, "02:30", 60, springForward.toString(), springForward.toString(), null));

        ResolvedAvailabilityResponse resolved =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(resolved.dstGapDays()).containsExactly(springForward.toString());
        assertThat(resolved.occurrences()).hasSize(1);
    }

    // ---------------------------------------------------------------------
    // Window filter.
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_windowFilter_narrowsToIntersectingOccurrences() {
        AvailabilityWriteService writeService = writeServiceWithClock(FIXED_CLOCK);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        DOW_A,
                        "09:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.plusDays(14).toString(),
                        null));
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        DOW_B,
                        "14:00",
                        60,
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.plusDays(14).toString(),
                        null));
        List<GuideAvailabilityOccurrenceEntity> persisted =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(persisted).hasSize(5);

        LocalDate from = persisted.get(1).getDuringStartAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate to =
                persisted
                        .get(3)
                        .getDuringStartAt()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .plusDays(1);

        ResolvedAvailabilityResponse resolved =
                readService.getResolvedAvailability(
                        actingUser(guideAUserId), from.toString(), to.toString());

        assertThat(resolved.occurrences())
                .extracting(ResolvedOccurrence::startAt)
                .containsExactly(
                        persisted.get(1).getDuringStartAt(),
                        persisted.get(2).getDuringStartAt(),
                        persisted.get(3).getDuringStartAt());
    }

    @Test
    void getResolvedAvailability_windowFilter_boundaryIsHalfOpen() {
        AvailabilityWriteService writeService = writeServiceWithClock(FIXED_CLOCK);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(
                        DOW_A, "09:00", 60, FIXED_TODAY.toString(), FIXED_TODAY.toString(), null));
        GuideAvailabilityOccurrenceEntity only =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId).get(0);
        LocalDate occurrenceDate = only.getDuringStartAt().atZone(ZoneOffset.UTC).toLocalDate();

        // Fully inside [occurrenceDate, occurrenceDate + 1) -> included.
        assertThat(
                        readService
                                .getResolvedAvailability(
                                        actingUser(guideAUserId),
                                        occurrenceDate.toString(),
                                        occurrenceDate.plusDays(1).toString())
                                .occurrences())
                .hasSize(1);

        // to == occurrenceDate (window ends exactly at the occurrence's own day start) -> excluded.
        assertThat(
                        readService
                                .getResolvedAvailability(
                                        actingUser(guideAUserId), null, occurrenceDate.toString())
                                .occurrences())
                .isEmpty();

        // from == occurrenceDate + 1 (window starts the day after the occurrence) -> excluded.
        assertThat(
                        readService
                                .getResolvedAvailability(
                                        actingUser(guideAUserId),
                                        occurrenceDate.plusDays(1).toString(),
                                        null)
                                .occurrences())
                .isEmpty();
    }

    @Test
    void getResolvedAvailability_windowFilter_parsesBoundInGuideLocalTimezone() {
        // Guide in LA (UTC-7 in July). An occurrence at 22:00-23:00 on 2026-07-15 LOCAL is
        // 2026-07-16 05:00-06:00 UTC -- its LOCAL calendar date is the 15th. Filtering
        // to = "2026-07-16" (exclusive) must INCLUDE it. Parsing `to` as UTC midnight puts the
        // boundary at 2026-07-16T00:00Z, BEFORE the occurrence's 05:00Z start, wrongly excluding
        // it;
        // parsing in the guide's LA zone puts it at 2026-07-16T07:00Z, correctly including it.
        settingsRepo.save(laSettings(guideAId));
        Instant start = LocalDate.of(2026, 7, 15).atTime(22, 0).atZone(ZoneId.of(LA)).toInstant();
        Instant end = LocalDate.of(2026, 7, 15).atTime(23, 0).atZone(ZoneId.of(LA)).toInstant();
        seedOccurrence(guideAId, start, end);

        ResolvedAvailabilityResponse resolved =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, "2026-07-16");

        assertThat(resolved.occurrences())
                .extracting(ResolvedOccurrence::startAt)
                .containsExactly(start);
    }

    // ---------------------------------------------------------------------
    // Validation.
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_rejectsMalformedFrom() {
        assertThatThrownBy(
                        () ->
                                readService.getResolvedAvailability(
                                        actingUser(guideAUserId), "not-a-date", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void getResolvedAvailability_rejectsMalformedTo() {
        assertThatThrownBy(
                        () ->
                                readService.getResolvedAvailability(
                                        actingUser(guideAUserId), null, "not-a-date"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void getResolvedAvailability_rejectsToNotAfterFrom() {
        assertThatThrownBy(
                        () ->
                                readService.getResolvedAvailability(
                                        actingUser(guideAUserId), "2026-07-11", "2026-07-11"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(
                        () ->
                                readService.getResolvedAvailability(
                                        actingUser(guideAUserId), "2026-07-11", "2026-07-01"))
                .isInstanceOf(ValidationException.class);
    }

    // ---------------------------------------------------------------------
    // Owner scoping.
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_scopesToTheCallersOwnGuide() {
        AvailabilityWriteService writeService = writeServiceWithClock(FIXED_CLOCK);
        writeService.createRule(
                actingUser(guideAUserId),
                new AvailabilityRuleRequest(DOW_A, "09:00", 60, null, null, null));
        writeService.createRule(
                actingUser(guideBUserId),
                new AvailabilityRuleRequest(DOW_B, "08:00", 30, null, null, null));

        ResolvedAvailabilityResponse guideAView =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);
        ResolvedAvailabilityResponse guideBView =
                readService.getResolvedAvailability(actingUser(guideBUserId), null, null);

        assertThat(guideAView.rules()).hasSize(1);
        assertThat(guideAView.rules().get(0).startLocal()).isEqualTo("09:00");
        assertThat(guideAView.occurrences()).isNotEmpty();

        assertThat(guideBView.rules()).hasSize(1);
        assertThat(guideBView.rules().get(0).startLocal()).isEqualTo("08:00");
        assertThat(guideBView.occurrences()).isNotEmpty();

        // Isolation: guideA's view never includes guideB's occurrences and vice versa.
        assertThat(guideAView.occurrences()).doesNotContainAnyElementsOf(guideBView.occurrences());
    }

    // ---------------------------------------------------------------------
    // Empty case.
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_returnsEmptyLists_whenGuideHasNoRules() {
        ResolvedAvailabilityResponse resolved =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(resolved.rules()).isEmpty();
        assertThat(resolved.occurrences()).isEmpty();
        assertThat(resolved.dstGapDays()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Readiness signals -- bookable (occurrence) + hasWeeklyHours (rule) (B1).
    //
    // Four-quadrant matrix over the two independent derived booleans:
    //   (1) ADDITIONAL-only future occurrence, no rule -> bookable true,  hasWeeklyHours false
    //   (2) expired ACTIVE rule, zero future occurrence -> bookable false, hasWeeklyHours true
    //   (3) active rule + future occurrence             -> bookable true,  hasWeeklyHours true
    //   (4) brand-new guide (nothing)                   -> bookable false, hasWeeklyHours false
    // ---------------------------------------------------------------------

    @Test
    void readiness_additionalOnlyOccurrence_bookableTrue_hasWeeklyHoursFalse() {
        // An ADDITIONAL-style future occurrence with NO weekly rule behind it.
        Instant start = Instant.now().plus(Duration.ofDays(2));
        seedOccurrence(guideAId, start, start.plus(Duration.ofHours(1)));

        ResolvedAvailabilityResponse s =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(s.bookable()).isTrue();
        assertThat(s.hasWeeklyHours()).isFalse();
    }

    @Test
    void readiness_expiredActiveRule_bookableFalse_hasWeeklyHoursTrue() {
        // active = true (a soft-delete/enable flag) with effective_to in the PAST -> the rule still
        // counts for hasWeeklyHours, but rematerialize yields no future occurrence, so NOT
        // bookable.
        // Must be an EXPIRED rule, never an inactive one (inactive would make hasWeeklyHours false
        // and miss this quadrant).
        seedRule(guideAId, true, FIXED_TODAY.minusDays(30), FIXED_TODAY.minusDays(1));

        ResolvedAvailabilityResponse s =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(s.bookable()).isFalse();
        assertThat(s.hasWeeklyHours()).isTrue();
    }

    @Test
    void readiness_healthyActiveRuleWithFutureOccurrence_bothTrue() {
        seedRule(guideAId, true, FIXED_TODAY.minusDays(1), FIXED_TODAY.plusDays(30));
        Instant start = Instant.now().plus(Duration.ofDays(2));
        seedOccurrence(guideAId, start, start.plus(Duration.ofHours(1)));

        ResolvedAvailabilityResponse s =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(s.bookable()).isTrue();
        assertThat(s.hasWeeklyHours()).isTrue();
    }

    @Test
    void readiness_brandNewGuide_bothFalse() {
        ResolvedAvailabilityResponse s =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(s.bookable()).isFalse();
        assertThat(s.hasWeeklyHours()).isFalse();
    }

    // A past occurrence (already ended) must NOT count as bookable -- guards the "After now" edge.
    @Test
    void readiness_onlyPastOccurrence_bookableFalse() {
        Instant end = Instant.now().minus(Duration.ofDays(1));
        seedOccurrence(guideAId, end.minus(Duration.ofHours(1)), end);

        ResolvedAvailabilityResponse s =
                readService.getResolvedAvailability(actingUser(guideAUserId), null, null);

        assertThat(s.bookable()).isFalse();
        assertThat(s.hasWeeklyHours()).isFalse();
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private GuideAvailabilityOccurrenceEntity seedOccurrence(
            UUID guideId, Instant start, Instant end) {
        GuideAvailabilityOccurrenceEntity o = new GuideAvailabilityOccurrenceEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guideId);
        o.setDuringStartAt(start);
        o.setDuringEndAt(end);
        o.setGeneratedAt(Instant.now());
        return occurrences.save(o);
    }

    private static GuideBookingSettingsEntity laSettings(UUID guideId) {
        GuideBookingSettingsEntity s = new GuideBookingSettingsEntity();
        s.setGuideId(guideId);
        s.setTimezone(LA);
        return s;
    }

    private GuideAvailabilityRuleEntity seedRule(
            UUID guideId, boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
        GuideAvailabilityRuleEntity r = new GuideAvailabilityRuleEntity();
        r.setId(UUID.randomUUID());
        r.setGuideId(guideId);
        r.setDayOfWeek((short) DOW_A);
        r.setStartLocal(LocalTime.of(9, 0));
        r.setWindowMin(60);
        r.setTimezone(LA);
        r.setEffectiveFrom(effectiveFrom);
        r.setEffectiveTo(effectiveTo);
        r.setActive(active);
        return rules.save(r);
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

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("rt-" + UUID.randomUUID());
        u.setEmail("rt-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone(LA);
        return u;
    }
}
