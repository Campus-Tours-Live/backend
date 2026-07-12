package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.OverridePreviewRequest;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.OverridePreviewResponse.DatePreview;
import com.CampusToursLive.web.dto.OverridePreviewResponse.TrimmedSegment;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * Persistence integration test for {@link AvailabilityPreviewService} (CTL-54 v2.1 Task 4) against
 * a REAL PostgreSQL (Testcontainers) -- the only way to exercise a genuine {@link
 * AvailabilityExceptionRepository#findByGuideIdAndExceptionDate} read alongside {@link
 * AvailabilityWriteService}'s real trim/replace write path, so the preview can be asserted to MATCH
 * an actual save while never mutating the database itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityPreviewServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 7, 11);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final ZoneId LA_ZONE = ZoneId.of("America/Los_Angeles");

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

    private AvailabilityPreviewService previewService;
    private AvailabilityWriteService writeService;

    private UUID guideAUserId;
    private UUID guideAId;
    private UUID guideBId;

    @BeforeEach
    void setUp() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        UUID universityId = university.getId();

        GuideProfileEntity guideA = seedGuide("Guide A", universityId);
        guideAUserId = guideA.getUserId();
        guideAId = guideA.getId();
        guideBId = seedGuide("Guide B", universityId).getId();

        previewService = new AvailabilityPreviewService(rules, exceptions, settingsRepo);

        AvailabilityService availabilityService =
                new AvailabilityService(
                        rules, exceptions, occurrences, dstNotices, settingsRepo, FIXED_CLOCK);
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
    // Matches an actual save, without persisting anything.
    // ---------------------------------------------------------------------

    @Test
    void preview_matchesActualSave_withoutPersistingAnything() {
        LocalDate d = FIXED_TODAY;
        seedException(guideAId, d, AvailabilityExceptionKind.ADDITIONAL, "09:00", 60, null);

        int rowCountBefore = exceptions.findByGuideIdAndExceptionDate(guideAId, d).size();

        OverridePreviewRequest req =
                new OverridePreviewRequest(d.toString(), d.toString(), "UNAVAILABLE", "09:30", 90);
        OverridePreviewResponse preview = previewService.preview(guideAId, req);

        // Nothing was persisted -- the row count for this guide/date is unchanged.
        assertThat(exceptions.findByGuideIdAndExceptionDate(guideAId, d)).hasSize(rowCountBefore);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.days()).hasSize(1);
        DatePreview dp = preview.days().get(0);
        assertThat(dp.date()).isEqualTo(d.toString());
        assertThat(dp.resultingWindows()).hasSize(1);
        assertThat(dp.resultingWindows().get(0).startAt())
                .isEqualTo(d.atTime(9, 0).atZone(LA_ZONE).toInstant());
        assertThat(dp.resultingWindows().get(0).endAt())
                .isEqualTo(d.atTime(9, 30).atZone(LA_ZONE).toInstant());
        assertThat(dp.trimmed())
                .extracting(
                        TrimmedSegment::kind, TrimmedSegment::startLocal, TrimmedSegment::windowMin)
                .containsExactly(tuple("ADDITIONAL", "09:00", 60));

        // Cross-check against an ACTUAL save (T3's write path) producing the SAME result.
        writeService.createException(
                actingUser(guideAUserId),
                new AvailabilityExceptionRequest(d.toString(), "UNAVAILABLE", "09:30", 90, null));
        List<GuideAvailabilityOccurrenceEntity> occ =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideAId);
        assertThat(occ).hasSize(1);
        assertThat(occ.get(0).getDuringStartAt()).isEqualTo(dp.resultingWindows().get(0).startAt());
        assertThat(occ.get(0).getDuringEndAt()).isEqualTo(dp.resultingWindows().get(0).endAt());
    }

    // ---------------------------------------------------------------------
    // Full-list correctness -- guards the write-path-vs-preview subset gotcha.
    // ---------------------------------------------------------------------

    @Test
    void preview_reflectsBothOverlappingAndNonOverlappingExistingExceptions() {
        LocalDate d = FIXED_TODAY;
        // Overlaps the proposed override.
        seedException(guideAId, d, AvailabilityExceptionKind.ADDITIONAL, "09:00", 60, null);
        // Does NOT overlap -- must still contribute to the resulting net-available windows.
        seedException(guideAId, d, AvailabilityExceptionKind.ADDITIONAL, "14:00", 60, null);

        OverridePreviewRequest req =
                new OverridePreviewRequest(d.toString(), d.toString(), "UNAVAILABLE", "09:30", 90);
        OverridePreviewResponse preview = previewService.preview(guideAId, req);

        DatePreview dp = preview.days().get(0);
        assertThat(dp.resultingWindows())
                .extracting(o -> o.startAt(), o -> o.endAt())
                .containsExactlyInAnyOrder(
                        tuple(
                                d.atTime(9, 0).atZone(LA_ZONE).toInstant(),
                                d.atTime(9, 30).atZone(LA_ZONE).toInstant()),
                        tuple(
                                d.atTime(14, 0).atZone(LA_ZONE).toInstant(),
                                d.atTime(15, 0).atZone(LA_ZONE).toInstant()));
        // Only the overlapping exception is reported as trimmed.
        assertThat(dp.trimmed()).extracting(TrimmedSegment::startLocal).containsExactly("09:00");
    }

    // ---------------------------------------------------------------------
    // Multi-day.
    // ---------------------------------------------------------------------

    @Test
    void preview_multiDay_returnsOneDatePreviewPerDate() {
        LocalDate d0 = FIXED_TODAY;
        LocalDate d1 = d0.plusDays(1);

        OverridePreviewRequest req =
                new OverridePreviewRequest(
                        d0.toString(), d1.toString(), "UNAVAILABLE", "09:00", 60);
        OverridePreviewResponse preview = previewService.preview(guideAId, req);

        assertThat(preview.days())
                .extracting(DatePreview::date)
                .containsExactly(d0.toString(), d1.toString());
    }

    // ---------------------------------------------------------------------
    // Owner-scoped.
    // ---------------------------------------------------------------------

    @Test
    void preview_isScopedToTheRequestedGuide_neverLeaksAnotherGuidesExceptions() {
        LocalDate d = FIXED_TODAY;
        seedException(guideAId, d, AvailabilityExceptionKind.ADDITIONAL, "09:00", 60, null);

        OverridePreviewRequest req =
                new OverridePreviewRequest(d.toString(), d.toString(), "UNAVAILABLE", "09:30", 90);
        OverridePreviewResponse preview = previewService.preview(guideBId, req);

        // Guide B has no exceptions of its own -- guide A's ADDITIONAL must never leak in.
        DatePreview dp = preview.days().get(0);
        assertThat(dp.trimmed()).isEmpty();
        assertThat(dp.resultingWindows()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Shared guard -- same 422s as create, before any Span is built.
    // ---------------------------------------------------------------------

    @Test
    void preview_rejectsDateRangeOver366Days() {
        LocalDate from = FIXED_TODAY;
        LocalDate to = from.plusDays(367);
        OverridePreviewRequest req =
                new OverridePreviewRequest(
                        from.toString(), to.toString(), "UNAVAILABLE", "09:00", 60);

        assertThatThrownBy(() -> previewService.preview(guideAId, req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void preview_rejectsOverrideCrossingMidnight_beforeAnySpanOf() {
        OverridePreviewRequest req =
                new OverridePreviewRequest(
                        FIXED_TODAY.toString(),
                        FIXED_TODAY.toString(),
                        "UNAVAILABLE",
                        "22:00",
                        240);

        assertThatThrownBy(() -> previewService.preview(guideAId, req))
                .isInstanceOf(ValidationException.class);
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private void seedException(
            UUID guideId,
            LocalDate date,
            AvailabilityExceptionKind kind,
            String startLocal,
            int windowMin,
            String reason) {
        AvailabilityExceptionEntity e = new AvailabilityExceptionEntity();
        e.setId(UUID.randomUUID());
        e.setGuideId(guideId);
        e.setExceptionDate(date);
        e.setKind(kind);
        e.setStartLocal(LocalTime.parse(startLocal));
        e.setWindowMin(windowMin);
        e.setReason(reason);
        exceptions.save(e);
    }

    private GuideProfileEntity seedGuide(String displayName, UUID universityId) {
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
        u.setOidcSubject("pt-" + UUID.randomUUID());
        u.setEmail("pt-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }
}
