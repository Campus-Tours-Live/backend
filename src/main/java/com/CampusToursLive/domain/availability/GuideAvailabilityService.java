package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilitySummaryResponse;
import com.CampusToursLive.web.dto.BookingSettingsResponse;
import com.CampusToursLive.web.dto.CreateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.CreateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateBookingSettingsRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuideAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(GuideAvailabilityService.class);
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 45, 60, 90);
    private static final DateTimeFormatter TIME_HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideBookingSettingsRepository bookingSettings;
    private final GuideBookingSettingsService settingsService;
    private final GuideProfileRepository guides;
    private final ObjectMapper mapper;

    public GuideAvailabilityService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository bookingSettings,
            GuideBookingSettingsService settingsService,
            GuideProfileRepository guides,
            ObjectMapper mapper) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.bookingSettings = bookingSettings;
        this.settingsService = settingsService;
        this.guides = guides;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AvailabilitySummaryResponse getSummary(UserEntity user) {
        GuideProfileEntity guide = requireGuideProfile(user);
        UUID guideId = guide.getId();
        return new AvailabilitySummaryResponse(
                rules.findByGuideIdOrderByDayOfWeekAscStartLocalAsc(guideId).stream()
                        .map(this::toRuleResponse)
                        .toList(),
                exceptions.findByGuideIdOrderByExceptionDateAscCreatedAtAsc(guideId).stream()
                        .map(this::toExceptionResponse)
                        .toList(),
                toSettingsResponse(loadSettings(guideId)));
    }

    @Transactional
    public AvailabilityRuleResponse createRule(UserEntity user, CreateAvailabilityRuleRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);
        GuideBookingSettingsEntity settings = requireSettings(guide.getId());

        log.info(
                "createRule received startLocal={} endLocal={} dayOfWeek={} guideId={}",
                req.startLocal(),
                req.endLocal(),
                req.dayOfWeek(),
                guide.getId());

        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guide.getId());
        rule.setDayOfWeek(parseDayOfWeek(req.dayOfWeek()));
        rule.setStartLocal(parseTime(req.startLocal(), "startLocal"));
        rule.setEndLocal(parseTime(req.endLocal(), "endLocal"));
        validateTimeRange(rule.getStartLocal(), rule.getEndLocal());
        log.info(
                "createRule parsed startLocal={} endLocal={}",
                rule.getStartLocal(),
                rule.getEndLocal());
        rule.setTimezone(
                AvailabilityTimezones.resolveRuleTimezone(req.timezone(), settings.getTimezone()));
        rule.setEffectiveFrom(parseDate(req.effectiveFrom(), "effectiveFrom", LocalDate.now()));
        rule.setEffectiveTo(parseOptionalDate(req.effectiveTo(), "effectiveTo"));
        validateEffectiveRange(rule.getEffectiveFrom(), rule.getEffectiveTo());
        rule.setActive(req.active() == null || req.active());

        assertNoRuleOverlap(rule, null);
        log.info(
                "createRule saving startLocal={} endLocal={}",
                rule.getStartLocal(),
                rule.getEndLocal());
        rules.save(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public AvailabilityRuleResponse updateRule(
            UserEntity user, UUID ruleId, UpdateAvailabilityRuleRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);
        GuideBookingSettingsEntity settings = requireSettings(guide.getId());
        GuideAvailabilityRuleEntity rule =
                rules.findByIdAndGuideId(ruleId, guide.getId())
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));

        log.info(
                "updateRule received ruleId={} startLocal={} endLocal={} dayOfWeek={} guideId={}",
                ruleId,
                req.startLocal(),
                req.endLocal(),
                req.dayOfWeek(),
                guide.getId());
        log.info(
                "updateRule existing startLocal={} endLocal={}",
                rule.getStartLocal(),
                rule.getEndLocal());

        if (req.dayOfWeek() != null) rule.setDayOfWeek(parseDayOfWeek(req.dayOfWeek()));
        if (req.startLocal() != null) rule.setStartLocal(parseTime(req.startLocal(), "startLocal"));
        if (req.endLocal() != null) rule.setEndLocal(parseTime(req.endLocal(), "endLocal"));
        validateTimeRange(rule.getStartLocal(), rule.getEndLocal());
        log.info(
                "updateRule parsed startLocal={} endLocal={}",
                rule.getStartLocal(),
                rule.getEndLocal());
        if (req.timezone() != null) {
            rule.setTimezone(
                    AvailabilityTimezones.resolveRuleTimezone(
                            req.timezone(), settings.getTimezone()));
        } else {
            AvailabilityTimezones.resolveRuleTimezone(rule.getTimezone(), settings.getTimezone());
        }
        if (req.effectiveFrom() != null) {
            rule.setEffectiveFrom(parseDate(req.effectiveFrom(), "effectiveFrom", null));
        }
        if (req.effectiveTo() != null) {
            rule.setEffectiveTo(parseOptionalDate(req.effectiveTo(), "effectiveTo"));
        }
        if (req.active() != null) rule.setActive(req.active());
        validateEffectiveRange(rule.getEffectiveFrom(), rule.getEffectiveTo());

        assertNoRuleOverlap(rule, rule.getId());
        log.info(
                "updateRule saving startLocal={} endLocal={}",
                rule.getStartLocal(),
                rule.getEndLocal());
        rules.save(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public void deleteRule(UserEntity user, UUID ruleId) {
        GuideProfileEntity guide = requireGuideProfile(user);
        GuideAvailabilityRuleEntity rule =
                rules.findByIdAndGuideId(ruleId, guide.getId())
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));
        rules.delete(rule);
    }

    @Transactional
    public AvailabilityExceptionResponse createException(
            UserEntity user, CreateAvailabilityExceptionRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);

        AvailabilityExceptionEntity ex = new AvailabilityExceptionEntity();
        ex.setId(UUID.randomUUID());
        ex.setGuideId(guide.getId());
        ex.setExceptionDate(parseDate(req.exceptionDate(), "exceptionDate", null));
        ex.setType(parseExceptionType(req.type()));
        applyExceptionTimes(ex, req.startLocal(), req.endLocal());
        ex.setReason(trimToNull(req.reason()));

        exceptions.save(ex);
        return toExceptionResponse(ex);
    }

    @Transactional
    public AvailabilityExceptionResponse updateException(
            UserEntity user, UUID exceptionId, UpdateAvailabilityExceptionRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);
        AvailabilityExceptionEntity ex =
                exceptions
                        .findByIdAndGuideId(exceptionId, guide.getId())
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));

        if (req.exceptionDate() != null) {
            ex.setExceptionDate(parseDate(req.exceptionDate(), "exceptionDate", null));
        }
        if (req.type() != null) ex.setType(parseExceptionType(req.type()));
        if (req.startLocal() != null || req.endLocal() != null || req.type() != null) {
            applyExceptionTimes(
                    ex,
                    req.startLocal() != null ? req.startLocal() : formatTime(ex.getStartLocal()),
                    req.endLocal() != null ? req.endLocal() : formatTime(ex.getEndLocal()));
        }
        if (req.reason() != null) ex.setReason(trimToNull(req.reason()));

        exceptions.save(ex);
        return toExceptionResponse(ex);
    }

    @Transactional
    public void deleteException(UserEntity user, UUID exceptionId) {
        GuideProfileEntity guide = requireGuideProfile(user);
        AvailabilityExceptionEntity ex =
                exceptions
                        .findByIdAndGuideId(exceptionId, guide.getId())
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));
        exceptions.delete(ex);
    }

    @Transactional
    public BookingSettingsResponse updateBookingSettings(
            UserEntity user, UpdateBookingSettingsRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);
        GuideBookingSettingsEntity settings = requireSettings(guide.getId());

        if (req.acceptanceMode() != null) {
            settings.setAcceptanceMode(parseAcceptanceMode(req.acceptanceMode()));
        }
        if (req.responseDeadlineMin() != null) {
            validatePositive(req.responseDeadlineMin(), "responseDeadlineMin");
            settings.setResponseDeadlineMin(req.responseDeadlineMin());
        }
        if (req.minNoticeMin() != null) {
            validateNonNegative(req.minNoticeMin(), "minNoticeMin");
            settings.setMinNoticeMin(req.minNoticeMin());
        }
        if (req.maxAdvanceDays() != null) {
            validatePositive(req.maxAdvanceDays(), "maxAdvanceDays");
            settings.setMaxAdvanceDays(req.maxAdvanceDays());
        }
        if (req.bufferBeforeMin() != null) {
            validateNonNegative(req.bufferBeforeMin(), "bufferBeforeMin");
            settings.setBufferBeforeMin(req.bufferBeforeMin());
        }
        if (req.bufferAfterMin() != null) {
            validateNonNegative(req.bufferAfterMin(), "bufferAfterMin");
            settings.setBufferAfterMin(req.bufferAfterMin());
        }
        if (req.durationsOffered() != null) {
            settings.setDurationsOffered(writeDurations(req.durationsOffered()));
        }
        if (req.timezone() != null) {
            settings.setTimezone(AvailabilityTimezones.validateTimezone(req.timezone()));
        }

        bookingSettings.save(settings);
        return toSettingsResponse(settings);
    }

    private GuideProfileEntity requireGuideProfile(UserEntity user) {
        return guides.findByUserId(user.getId())
                .orElseThrow(
                        () ->
                                new ValidationException(
                                        "No guide profile — complete guide onboarding first"));
    }

    private GuideBookingSettingsEntity loadSettings(UUID guideId) {
        return bookingSettings
                .findById(guideId)
                .orElseGet(() -> settingsService.getOrCreate(guideId));
    }

    private GuideBookingSettingsEntity requireSettings(UUID guideId) {
        return settingsService.getOrCreate(guideId);
    }

    private void assertNoRuleOverlap(GuideAvailabilityRuleEntity candidate, UUID excludeRuleId) {
        if (!candidate.isActive()) return;

        List<GuideAvailabilityRuleEntity> sameDay =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(
                        candidate.getGuideId(), candidate.getDayOfWeek());
        for (GuideAvailabilityRuleEntity other : sameDay) {
            if (excludeRuleId != null && other.getId().equals(excludeRuleId)) continue;
            if (!effectiveRangesOverlap(candidate, other)) continue;
            if (timesOverlap(
                    candidate.getStartLocal(),
                    candidate.getEndLocal(),
                    other.getStartLocal(),
                    other.getEndLocal())) {
                throw new ValidationException("This time block overlaps an existing rule");
            }
        }
    }

    private static boolean timesOverlap(
            LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    private static boolean effectiveRangesOverlap(
            GuideAvailabilityRuleEntity a, GuideAvailabilityRuleEntity b) {
        LocalDate aEnd = a.getEffectiveTo() != null ? a.getEffectiveTo() : LocalDate.MAX;
        LocalDate bEnd = b.getEffectiveTo() != null ? b.getEffectiveTo() : LocalDate.MAX;
        return !a.getEffectiveFrom().isAfter(bEnd) && !b.getEffectiveFrom().isAfter(aEnd);
    }

    private void applyExceptionTimes(
            AvailabilityExceptionEntity ex, String startLocal, String endLocal) {
        if (ex.getType() == AvailabilityExceptionType.UNAVAILABLE_ALL_DAY) {
            ex.setStartLocal(null);
            ex.setEndLocal(null);
            return;
        }
        LocalTime start = parseTime(startLocal, "startLocal");
        LocalTime end = parseTime(endLocal, "endLocal");
        validateTimeRange(start, end);
        ex.setStartLocal(start);
        ex.setEndLocal(end);
    }

    private AvailabilityRuleResponse toRuleResponse(GuideAvailabilityRuleEntity r) {
        return new AvailabilityRuleResponse(
                r.getId().toString(),
                r.getDayOfWeek(),
                formatTime(r.getStartLocal()),
                formatTime(r.getEndLocal()),
                r.getTimezone(),
                r.getEffectiveFrom().toString(),
                r.getEffectiveTo() != null ? r.getEffectiveTo().toString() : null,
                r.isActive(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
    }

    private AvailabilityExceptionResponse toExceptionResponse(AvailabilityExceptionEntity e) {
        return new AvailabilityExceptionResponse(
                e.getId().toString(),
                e.getExceptionDate().toString(),
                e.getType().name(),
                formatTime(e.getStartLocal()),
                formatTime(e.getEndLocal()),
                e.getReason(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    }

    private BookingSettingsResponse toSettingsResponse(GuideBookingSettingsEntity s) {
        return new BookingSettingsResponse(
                s.getAcceptanceMode().name(),
                s.getResponseDeadlineMin(),
                s.getMinNoticeMin(),
                s.getMaxAdvanceDays(),
                s.getBufferBeforeMin(),
                s.getBufferAfterMin(),
                readDurations(s.getDurationsOffered()),
                s.getTimezone());
    }

    private List<Integer> readDurations(String json) {
        try {
            List<Integer> values = mapper.readValue(json, new TypeReference<>() {});
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of(30, 45, 60, 90);
        }
    }

    private String writeDurations(List<Integer> durations) {
        if (durations == null || durations.isEmpty()) {
            throw new ValidationException("durationsOffered must not be empty");
        }
        for (Integer d : durations) {
            if (d == null || !ALLOWED_DURATIONS.contains(d)) {
                throw new ValidationException("durationsOffered must be subset of 30, 45, 60, 90");
            }
        }
        try {
            return mapper.writeValueAsString(durations);
        } catch (Exception ex) {
            throw new ValidationException("Invalid durationsOffered");
        }
    }

    private static short parseDayOfWeek(Integer raw) {
        if (raw == null) throw new ValidationException("dayOfWeek is required");
        if (raw < 0 || raw > 6) throw new ValidationException("dayOfWeek must be between 0 and 6");
        return raw.shortValue();
    }

    private LocalTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(field + " is required");
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.length() == 8) {
                return LocalTime.parse(trimmed, TIME_HH_MM_SS);
            }
            if (trimmed.length() == 5) {
                return LocalTime.parse(trimmed, TIME_HH_MM);
            }
            throw new ValidationException("Invalid " + field + ": " + raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid " + field + ": " + raw);
        }
    }

    private LocalDate parseDate(String raw, String field, LocalDate defaultValue) {
        if (raw == null || raw.isBlank()) {
            if (defaultValue != null) return defaultValue;
            throw new ValidationException(field + " is required");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new ValidationException("Invalid " + field + ": " + raw);
        }
    }

    private LocalDate parseOptionalDate(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        return parseDate(raw, field, null);
    }

    private static void validateTimeRange(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            throw new ValidationException("startLocal must be before endLocal");
        }
    }

    private static void validateEffectiveRange(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new ValidationException("effectiveTo must be on or after effectiveFrom");
        }
    }

    private AvailabilityExceptionType parseExceptionType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("type is required");
        }
        try {
            return AvailabilityExceptionType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid type: " + raw);
        }
    }

    private AcceptanceMode parseAcceptanceMode(String raw) {
        try {
            return AcceptanceMode.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid acceptanceMode: " + raw);
        }
    }

    private static String formatTime(LocalTime time) {
        return time == null ? null : time.toString().substring(0, 5);
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validatePositive(int value, String field) {
        if (value <= 0) throw new ValidationException(field + " must be positive");
    }

    private static void validateNonNegative(int value, String field) {
        if (value < 0) throw new ValidationException(field + " must be non-negative");
    }
}
