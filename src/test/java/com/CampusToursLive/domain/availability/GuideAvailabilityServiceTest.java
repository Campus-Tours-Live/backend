package com.CampusToursLive.domain.availability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AvailabilitySummaryResponse;
import com.CampusToursLive.web.dto.CreateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.CreateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateBookingSettingsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class GuideAvailabilityServiceTest {

    @Mock GuideAvailabilityRuleRepository rules;
    @Mock AvailabilityExceptionRepository exceptions;
    @Mock GuideBookingSettingsRepository bookingSettings;
    @Mock GuideBookingSettingsService settingsService;
    @Mock GuideProfileRepository guides;

    private final ObjectMapper mapper = new ObjectMapper();

    private GuideAvailabilityService service() {
        return new GuideAvailabilityService(
                rules, exceptions, bookingSettings, settingsService, guides, mapper);
    }

    private static UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private static GuideProfileEntity guide(UUID guideId, UUID userId) {
        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(guideId);
        g.setUserId(userId);
        return g;
    }

    private void stubGuide(UUID userId, UUID guideId) {
        when(guides.findByUserId(userId)).thenReturn(Optional.of(guide(guideId, userId)));
    }

    private GuideBookingSettingsEntity newSettings(UUID guideId) {
        GuideBookingSettingsEntity settings = new GuideBookingSettingsEntity();
        settings.setGuideId(guideId);
        return settings;
    }

    /**
     * Writable paths (create/update rule, update settings) use {@link GuideBookingSettingsService}.
     */
    private GuideBookingSettingsEntity stubSettings(UUID guideId) {
        GuideBookingSettingsEntity settings = newSettings(guideId);
        when(settingsService.getOrCreate(guideId)).thenReturn(settings);
        return settings;
    }

    /** Read-only summary loads settings from the repository first. */
    private GuideBookingSettingsEntity stubSettingsRead(UUID guideId) {
        GuideBookingSettingsEntity settings = newSettings(guideId);
        when(bookingSettings.findById(guideId)).thenReturn(Optional.of(settings));
        return settings;
    }

    private Answer<Object> echoSave() {
        return inv -> inv.getArgument(0);
    }

    @Test
    void getSummary_returnsRulesExceptionsAndSettings() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        when(guides.findByUserId(userId)).thenReturn(Optional.of(guide(guideId, userId)));

        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guideId);
        rule.setDayOfWeek((short) 1);
        rule.setStartLocal(LocalTime.of(10, 0));
        rule.setEndLocal(LocalTime.of(13, 0));
        rule.setTimezone("America/Los_Angeles");
        rule.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        rule.setActive(true);

        AvailabilityExceptionEntity ex = new AvailabilityExceptionEntity();
        ex.setId(UUID.randomUUID());
        ex.setGuideId(guideId);
        ex.setExceptionDate(LocalDate.parse("2026-06-25"));
        ex.setType(AvailabilityExceptionType.UNAVAILABLE_ALL_DAY);

        GuideBookingSettingsEntity settings = new GuideBookingSettingsEntity();
        settings.setGuideId(guideId);

        when(rules.findByGuideIdOrderByDayOfWeekAscStartLocalAsc(guideId))
                .thenReturn(List.of(rule));
        when(exceptions.findByGuideIdOrderByExceptionDateAscCreatedAtAsc(guideId))
                .thenReturn(List.of(ex));
        when(bookingSettings.findById(guideId)).thenReturn(Optional.of(settings));

        AvailabilitySummaryResponse summary = service().getSummary(user(userId));
        assertEquals(1, summary.rules().size());
        assertEquals("10:00", summary.rules().get(0).startLocal());
        assertEquals(1, summary.exceptions().size());
        assertEquals("UNAVAILABLE_ALL_DAY", summary.exceptions().get(0).type());
        assertEquals(1440, summary.bookingSettings().minNoticeMin());
    }

    @Test
    void createRule_rejectsOverlappingBlocks() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(UUID.randomUUID());
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setEffectiveFrom(LocalDate.parse("2026-01-01"));
        existing.setActive(true);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of(existing));

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "12:00", "14:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
        verify(rules, never()).save(any());
    }

    @Test
    void createRule_rejectsEndBeforeStart() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        0, "17:00", "06:00", "America/Los_Angeles", "2026-07-02", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
        verify(rules, never()).save(any());
    }

    @Test
    void updateRule_rejectsEndBeforeStart() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(ruleId);
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 0);
        existing.setStartLocal(LocalTime.of(9, 0));
        existing.setEndLocal(LocalTime.of(17, 0));
        existing.setTimezone("America/Los_Angeles");
        existing.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        existing.setActive(true);
        when(rules.findByIdAndGuideId(ruleId, guideId)).thenReturn(Optional.of(existing));

        UpdateAvailabilityRuleRequest req =
                new UpdateAvailabilityRuleRequest(null, "08:00", "00:15", null, null, null, null);

        assertThrows(
                ValidationException.class, () -> service().updateRule(user(userId), ruleId, req));
        verify(rules, never()).save(any());
    }

    @Test
    void createRule_persistsValidBlock() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 2))
                .thenReturn(List.of());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        2, "14:00", "17:00", "America/Los_Angeles", "2026-06-01", null, true);

        var resp = service().createRule(user(userId), req);
        assertEquals(2, resp.dayOfWeek());
        assertEquals("14:00", resp.startLocal());

        ArgumentCaptor<GuideAvailabilityRuleEntity> captor =
                ArgumentCaptor.forClass(GuideAvailabilityRuleEntity.class);
        verify(rules).save(captor.capture());
        assertEquals(guideId, captor.getValue().getGuideId());
    }

    @Test
    void createException_allDayClearsTimes() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        when(guides.findByUserId(userId)).thenReturn(Optional.of(guide(guideId, userId)));

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        "2026-07-04", "UNAVAILABLE_ALL_DAY", null, null, "Holiday");

        var resp = service().createException(user(userId), req);
        assertEquals("UNAVAILABLE_ALL_DAY", resp.type());
        assertNull(resp.startLocal());

        ArgumentCaptor<AvailabilityExceptionEntity> captor =
                ArgumentCaptor.forClass(AvailabilityExceptionEntity.class);
        verify(exceptions).save(captor.capture());
        assertNull(captor.getValue().getStartLocal());
        assertEquals("Holiday", captor.getValue().getReason());
    }

    @Test
    void deleteRule_throwsWhenNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        when(guides.findByUserId(userId)).thenReturn(Optional.of(guide(guideId, userId)));
        when(rules.findByIdAndGuideId(any(), eq(guideId))).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service().deleteRule(user(userId), UUID.randomUUID()));
    }

    @Test
    void getSummary_throwsWhenNoGuideProfile() {
        UUID userId = UUID.randomUUID();
        when(guides.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> service().getSummary(user(userId)));
    }

    @Test
    void getSummary_createsDefaultBookingSettingsWhenMissing() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(rules.findByGuideIdOrderByDayOfWeekAscStartLocalAsc(guideId)).thenReturn(List.of());
        when(exceptions.findByGuideIdOrderByExceptionDateAscCreatedAtAsc(guideId))
                .thenReturn(List.of());
        when(bookingSettings.findById(guideId)).thenReturn(Optional.empty());
        GuideBookingSettingsEntity created = new GuideBookingSettingsEntity();
        created.setGuideId(guideId);
        when(settingsService.getOrCreate(guideId)).thenReturn(created);

        AvailabilitySummaryResponse summary = service().getSummary(user(userId));
        assertEquals(1440, summary.bookingSettings().minNoticeMin());
        verify(settingsService).getOrCreate(guideId);
        verify(bookingSettings, never()).save(any());
    }

    @Test
    void getSummary_usesFallbackDurationsWhenJsonInvalid() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(rules.findByGuideIdOrderByDayOfWeekAscStartLocalAsc(guideId)).thenReturn(List.of());
        when(exceptions.findByGuideIdOrderByExceptionDateAscCreatedAtAsc(guideId))
                .thenReturn(List.of());
        GuideBookingSettingsEntity settings = stubSettingsRead(guideId);
        settings.setDurationsOffered("not-json");

        AvailabilitySummaryResponse summary = service().getSummary(user(userId));
        assertEquals(List.of(30, 45, 60, 90), summary.bookingSettings().durationsOffered());
    }

    @Test
    void updateRule_updatesSelectedFields() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(ruleId);
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setTimezone("America/Los_Angeles");
        existing.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        existing.setActive(true);
        when(rules.findByIdAndGuideId(ruleId, guideId)).thenReturn(Optional.of(existing));
        when(rules.save(any())).thenAnswer(echoSave());

        UpdateAvailabilityRuleRequest req =
                new UpdateAvailabilityRuleRequest(
                        2, "14:00", "17:00", null, "2026-07-01", "2026-12-31", false);

        var resp = service().updateRule(user(userId), ruleId, req);
        assertEquals(2, resp.dayOfWeek());
        assertEquals("14:00", resp.startLocal());
        assertEquals("America/Los_Angeles", resp.timezone());
        assertEquals("2026-12-31", resp.effectiveTo());
        assertFalse(resp.active());
    }

    @Test
    void deleteRule_removesWhenOwned() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        stubGuide(userId, guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(ruleId);
        existing.setGuideId(guideId);
        when(rules.findByIdAndGuideId(ruleId, guideId)).thenReturn(Optional.of(existing));

        service().deleteRule(user(userId), ruleId);
        verify(rules).delete(existing);
    }

    @Test
    void createRule_inactiveSkipsOverlapCheck() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "12:00", "14:00", "America/Los_Angeles", "2026-06-01", null, false);

        var resp = service().createRule(user(userId), req);
        assertFalse(resp.active());
        verify(rules, never()).findByGuideIdAndDayOfWeekAndActiveTrue(any(), anyShort());
    }

    @Test
    void createRule_rejectsInvalidDayOfWeek() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        7, "10:00", "12:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_rejectsInvalidTime() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "bad", "12:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_rejectsEffectiveToBeforeFrom() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1,
                        "10:00",
                        "12:00",
                        "America/Los_Angeles",
                        "2026-06-10",
                        "2026-06-01",
                        true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_acceptsSecondsInTime() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of());
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "09:00:00", "10:00:00", "America/Los_Angeles", "2026-06-01", null, true);

        var resp = service().createRule(user(userId), req);
        assertEquals("09:00", resp.startLocal());
    }

    @Test
    void createException_unavailableRange() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(exceptions.save(any())).thenAnswer(echoSave());

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        "2026-07-04", "UNAVAILABLE_RANGE", "10:00", "12:00", "Appointment");

        var resp = service().createException(user(userId), req);
        assertEquals("UNAVAILABLE_RANGE", resp.type());
        assertEquals("10:00", resp.startLocal());
        assertEquals("12:00", resp.endLocal());
    }

    @Test
    void createException_rejectsInvalidType() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        "2026-07-04", "NOT_A_TYPE", null, null, null);

        assertThrows(ValidationException.class, () -> service().createException(user(userId), req));
    }

    @Test
    void updateException_updatesTimesAndReason() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID exceptionId = UUID.randomUUID();
        stubGuide(userId, guideId);

        AvailabilityExceptionEntity existing = new AvailabilityExceptionEntity();
        existing.setId(exceptionId);
        existing.setGuideId(guideId);
        existing.setExceptionDate(LocalDate.parse("2026-07-04"));
        existing.setType(AvailabilityExceptionType.UNAVAILABLE_RANGE);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(12, 0));
        when(exceptions.findByIdAndGuideId(exceptionId, guideId)).thenReturn(Optional.of(existing));
        when(exceptions.save(any())).thenAnswer(echoSave());

        UpdateAvailabilityExceptionRequest req =
                new UpdateAvailabilityExceptionRequest(
                        "2026-07-05", null, "11:00", "13:00", "Updated");

        var resp = service().updateException(user(userId), exceptionId, req);
        assertEquals("2026-07-05", resp.exceptionDate());
        assertEquals("11:00", resp.startLocal());
        assertEquals("Updated", resp.reason());
    }

    @Test
    void updateException_allDayClearsTimes() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID exceptionId = UUID.randomUUID();
        stubGuide(userId, guideId);

        AvailabilityExceptionEntity existing = new AvailabilityExceptionEntity();
        existing.setId(exceptionId);
        existing.setGuideId(guideId);
        existing.setExceptionDate(LocalDate.parse("2026-07-04"));
        existing.setType(AvailabilityExceptionType.UNAVAILABLE_RANGE);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(12, 0));
        when(exceptions.findByIdAndGuideId(exceptionId, guideId)).thenReturn(Optional.of(existing));
        when(exceptions.save(any())).thenAnswer(echoSave());

        UpdateAvailabilityExceptionRequest req =
                new UpdateAvailabilityExceptionRequest(
                        null, "UNAVAILABLE_ALL_DAY", null, null, null);

        var resp = service().updateException(user(userId), exceptionId, req);
        assertEquals("UNAVAILABLE_ALL_DAY", resp.type());
        assertNull(resp.startLocal());
    }

    @Test
    void deleteException_removesWhenOwned() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID exceptionId = UUID.randomUUID();
        stubGuide(userId, guideId);

        AvailabilityExceptionEntity existing = new AvailabilityExceptionEntity();
        existing.setId(exceptionId);
        existing.setGuideId(guideId);
        when(exceptions.findByIdAndGuideId(exceptionId, guideId)).thenReturn(Optional.of(existing));

        service().deleteException(user(userId), exceptionId);
        verify(exceptions).delete(existing);
    }

    @Test
    void deleteException_throwsWhenNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(exceptions.findByIdAndGuideId(any(), eq(guideId))).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service().deleteException(user(userId), UUID.randomUUID()));
    }

    @Test
    void updateBookingSettings_updatesFields() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        GuideBookingSettingsEntity settings = stubSettings(guideId);
        when(bookingSettings.save(any())).thenAnswer(echoSave());

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        "AUTO", 120, 720, 45, 10, 20, List.of(60, 90), "America/New_York");

        var resp = service().updateBookingSettings(user(userId), req);
        assertEquals("AUTO", resp.acceptanceMode());
        assertEquals(720, resp.minNoticeMin());
        assertEquals(List.of(60, 90), resp.durationsOffered());
        assertEquals("America/New_York", settings.getTimezone());
    }

    @Test
    void updateBookingSettings_rejectsInvalidDuration() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        null, null, null, null, null, null, List.of(15), null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void updateBookingSettings_rejectsEmptyDurations() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        null, null, null, null, null, null, List.of(), null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void updateBookingSettings_rejectsNegativeMinNotice() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(null, null, -1, null, null, null, null, null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void updateBookingSettings_rejectsInvalidAcceptanceMode() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        "INVALID", null, null, null, null, null, null, null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void createRule_allowsNonOverlappingEffectiveRanges() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(UUID.randomUUID());
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setEffectiveFrom(LocalDate.parse("2026-01-01"));
        existing.setEffectiveTo(LocalDate.parse("2026-06-01"));
        existing.setActive(true);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of(existing));
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "10:00", "13:00", "America/Los_Angeles", "2026-07-01", null, true);

        var resp = service().createRule(user(userId), req);
        assertEquals("10:00", resp.startLocal());
    }

    @Test
    void createRule_defaultsActiveTrueWhenNull() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of());
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "10:00", "12:00", "America/Los_Angeles", "2026-06-01", null, null);

        var resp = service().createRule(user(userId), req);
        assertTrue(resp.active());
    }

    @Test
    void createRule_usesTodayWhenEffectiveFromMissing() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of());
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "10:00", "12:00", "America/Los_Angeles", null, null, true);

        var resp = service().createRule(user(userId), req);
        assertEquals(LocalDate.now().toString(), resp.effectiveFrom());
    }

    @Test
    void updateRule_overlapCheckExcludesSelf() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(ruleId);
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setTimezone("America/Los_Angeles");
        existing.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        existing.setActive(true);
        when(rules.findByIdAndGuideId(ruleId, guideId)).thenReturn(Optional.of(existing));
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of(existing));
        when(rules.save(any())).thenAnswer(echoSave());

        UpdateAvailabilityRuleRequest req =
                new UpdateAvailabilityRuleRequest(null, "11:00", "14:00", null, null, null, true);

        var resp = service().updateRule(user(userId), ruleId, req);
        assertEquals("11:00", resp.startLocal());
    }

    @Test
    void updateBookingSettings_rejectsNonPositiveResponseDeadline() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(null, 0, null, null, null, null, null, null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void createRule_rejectsNullDayOfWeek() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        null, "10:00", "12:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_rejectsBlankStartTime() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, " ", "12:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_rejectsInvalidEffectiveFrom() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "10:00", "12:00", "America/Los_Angeles", "not-a-date", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void createRule_allowsAdjacentNonOverlappingBlocks() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(UUID.randomUUID());
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setEffectiveFrom(LocalDate.parse("2026-01-01"));
        existing.setActive(true);
        when(rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, (short) 1))
                .thenReturn(List.of(existing));
        when(rules.save(any())).thenAnswer(echoSave());

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "13:00", "17:00", "America/Los_Angeles", "2026-06-01", null, true);

        var resp = service().createRule(user(userId), req);
        assertEquals("13:00", resp.startLocal());
    }

    @Test
    void updateRule_throwsWhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);
        when(rules.findByIdAndGuideId(any(), eq(guideId))).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        service()
                                .updateRule(
                                        user(userId),
                                        UUID.randomUUID(),
                                        new UpdateAvailabilityRuleRequest(
                                                null, null, null, null, null, null, null)));
    }

    @Test
    void updateException_throwsWhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(exceptions.findByIdAndGuideId(any(), eq(guideId))).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        service()
                                .updateException(
                                        user(userId),
                                        UUID.randomUUID(),
                                        new UpdateAvailabilityExceptionRequest(
                                                null, null, null, null, null)));
    }

    @Test
    void createException_rejectsBlankDate() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        " ", "UNAVAILABLE_ALL_DAY", null, null, null);

        assertThrows(ValidationException.class, () -> service().createException(user(userId), req));
    }

    @Test
    void createException_rejectsRangeEndBeforeStart() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        "2026-07-04", "UNAVAILABLE_RANGE", "14:00", "10:00", null);

        assertThrows(ValidationException.class, () -> service().createException(user(userId), req));
    }

    @Test
    void createException_trimsBlankReasonToNull() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        when(exceptions.save(any())).thenAnswer(echoSave());

        CreateAvailabilityExceptionRequest req =
                new CreateAvailabilityExceptionRequest(
                        "2026-07-04", "UNAVAILABLE_ALL_DAY", null, null, "   ");

        var resp = service().createException(user(userId), req);
        assertNull(resp.reason());
    }

    @Test
    void updateBookingSettings_rejectsNullDurationEntry() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.Arrays.asList(60, null),
                        null);

        assertThrows(
                ValidationException.class,
                () -> service().updateBookingSettings(user(userId), req));
    }

    @Test
    void getSummary_returnsNullCreatedAtWhenUnset() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);

        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guideId);
        rule.setDayOfWeek((short) 1);
        rule.setStartLocal(LocalTime.of(10, 0));
        rule.setEndLocal(LocalTime.of(13, 0));
        rule.setTimezone("America/Los_Angeles");
        rule.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        rule.setActive(true);

        when(rules.findByGuideIdOrderByDayOfWeekAscStartLocalAsc(guideId))
                .thenReturn(List.of(rule));
        when(exceptions.findByGuideIdOrderByExceptionDateAscCreatedAtAsc(guideId))
                .thenReturn(List.of());
        stubSettingsRead(guideId);

        AvailabilitySummaryResponse summary = service().getSummary(user(userId));
        assertNull(summary.rules().get(0).createdAt());
    }

    @Test
    void createRule_rejectsRuleTimezoneMismatchWithSettings() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "08:00", "10:00", "America/New_York", "2026-06-01", null, true);

        ValidationException ex =
                assertThrows(
                        ValidationException.class, () -> service().createRule(user(userId), req));
        assertEquals("Rule timezone must match booking settings timezone", ex.getMessage());
    }

    @Test
    void createRule_rejectsTimeWithTrailingGarbage() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        CreateAvailabilityRuleRequest req =
                new CreateAvailabilityRuleRequest(
                        1, "09:00xyz", "12:00", "America/Los_Angeles", "2026-06-01", null, true);

        assertThrows(ValidationException.class, () -> service().createRule(user(userId), req));
    }

    @Test
    void updateBookingSettings_rejectsInvalidTimezone() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        null, null, null, null, null, null, null, "Not/A/Timezone");

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().updateBookingSettings(user(userId), req));
        assertEquals("Invalid timezone: Not/A/Timezone", ex.getMessage());
    }

    @Test
    void updateRule_rejectsTimezoneMismatchWithSettings() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        GuideAvailabilityRuleEntity existing = new GuideAvailabilityRuleEntity();
        existing.setId(ruleId);
        existing.setGuideId(guideId);
        existing.setDayOfWeek((short) 1);
        existing.setStartLocal(LocalTime.of(10, 0));
        existing.setEndLocal(LocalTime.of(13, 0));
        existing.setTimezone("America/Los_Angeles");
        existing.setEffectiveFrom(LocalDate.parse("2026-06-01"));
        existing.setActive(true);
        when(rules.findByIdAndGuideId(ruleId, guideId)).thenReturn(Optional.of(existing));

        UpdateAvailabilityRuleRequest req =
                new UpdateAvailabilityRuleRequest(
                        null, null, null, "America/New_York", null, null, null);

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().updateRule(user(userId), ruleId, req));
        assertEquals("Rule timezone must match booking settings timezone", ex.getMessage());
    }

    @Test
    void updateBookingSettings_deduplicatesDurations() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        GuideBookingSettingsEntity settings = stubSettings(guideId);
        when(bookingSettings.save(any())).thenAnswer(echoSave());

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(
                        null, null, null, null, null, null, List.of(30, 30, 60), null);

        var resp = service().updateBookingSettings(user(userId), req);
        assertEquals(List.of(30, 60), resp.durationsOffered());
        assertEquals("[30,60]", settings.getDurationsOffered());
    }

    @Test
    void updateBookingSettings_rejectsExcessiveMaxAdvanceDays() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(null, null, null, 366, null, null, null, null);

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().updateBookingSettings(user(userId), req));
        assertEquals("maxAdvanceDays must be at most 365", ex.getMessage());
    }

    @Test
    void updateBookingSettings_rejectsExcessiveBuffer() {
        UUID userId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        stubGuide(userId, guideId);
        stubSettings(guideId);

        UpdateBookingSettingsRequest req =
                new UpdateBookingSettingsRequest(null, null, null, null, 1441, null, null, null);

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service().updateBookingSettings(user(userId), req));
        assertEquals("bufferBeforeMin must be at most 1440", ex.getMessage());
    }
}
