package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.booking.AcceptanceMode;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleRequest;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsUpdateRequest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide-facing availability WRITE API (CTL-54 Task 5): create/update/delete rules and exceptions,
 * plus read/update booking settings. This is deliberately a SEPARATE service from {@link
 * AvailabilityService} (which owns the pure "recompute + wholesale-replace occurrences" persistence
 * step, Task 3) — this class owns CRUD + validation + the guide-id / IDOR resolution, and calls
 * {@link AvailabilityService#rematerialize(UUID)} at the end of every write so the materialized
 * occurrences never lag the rules/exceptions/settings the guide just edited.
 *
 * <p><b>guideId</b> here is always {@code guide_profiles.id} (looked up from the authenticated
 * {@code UserEntity} via {@code guide_profiles.user_id}) — the same id {@code
 * guide_availability_rules.guide_id} / {@code availability_exceptions.guide_id} / {@code
 * guide_booking_settings.guide_id} reference, and the same resolution {@link
 * com.CampusToursLive.domain.tour.TourOfferingService} already uses for offerings.
 *
 * <p><b>IDOR safety.</b> Every by-id rule/exception lookup goes through {@code
 * findByIdAndGuideId(id, guideId)} — a row owned by a different guide is indistinguishable from a
 * nonexistent one (404), never leaked or mutated.
 *
 * <p><b>Settings auto-provisioning.</b> A rule/exception create first ensures the guide has a
 * settings row (inserting the V1 defaults if not), both so the new rule's timezone has a concrete
 * settings zone to copy and so {@link AvailabilityService#rematerialize} always has a settings tz
 * to resolve (closing the T3 "no settings row" edge at the source).
 */
@Service
public class AvailabilityWriteService {

    /** Matches the spec's global cap on any guide's {@code max_advance_days}. */
    static final int MAX_MAX_ADVANCE_DAYS = 365;

    private final GuideAvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final GuideBookingSettingsRepository settingsRepo;
    private final GuideProfileRepository guides;
    private final AvailabilityService availabilityService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public AvailabilityWriteService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo,
            GuideProfileRepository guides,
            AvailabilityService availabilityService,
            EntityManager entityManager) {
        this(
                rules,
                exceptions,
                settingsRepo,
                guides,
                availabilityService,
                entityManager,
                Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to pin the default {@code effectiveFrom}. */
    AvailabilityWriteService(
            GuideAvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions,
            GuideBookingSettingsRepository settingsRepo,
            GuideProfileRepository guides,
            AvailabilityService availabilityService,
            EntityManager entityManager,
            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.settingsRepo = settingsRepo;
        this.guides = guides;
        this.availabilityService = availabilityService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // Rules.
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> listRules(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return rules.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toRuleResponse)
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse createRule(UserEntity user, AvailabilityRuleRequest req) {
        UUID guideId = requireGuideId(user);
        validateRuleInput(req);

        GuideBookingSettingsEntity settings = getOrCreateSettings(guideId);

        GuideAvailabilityRuleEntity r = new GuideAvailabilityRuleEntity();
        r.setId(UUID.randomUUID());
        r.setGuideId(guideId);
        r.setDayOfWeek(req.dayOfWeek().shortValue());
        r.setStartLocal(parseLocalTime(req.startLocal()));
        r.setWindowMin(req.windowMin());
        // timezone is server-set = the guide's settings timezone (read-only-tz invariant), never
        // the client's.
        r.setTimezone(settings.getTimezone());
        r.setEffectiveFrom(
                req.effectiveFrom() != null
                        ? parseLocalDate(req.effectiveFrom())
                        : LocalDate.now(clock));
        r.setEffectiveTo(req.effectiveTo() != null ? parseLocalDate(req.effectiveTo()) : null);
        r.setActive(req.active() == null || req.active());
        validateEffectiveRange(r.getEffectiveFrom(), r.getEffectiveTo());

        rules.save(r);
        availabilityService.rematerialize(guideId);
        return toRuleResponse(r);
    }

    @Transactional
    public AvailabilityRuleResponse updateRule(
            UserEntity user, UUID id, AvailabilityRuleRequest req) {
        UUID guideId = requireGuideId(user);
        GuideAvailabilityRuleEntity r =
                rules.findByIdAndGuideId(id, guideId)
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));
        validateRuleInput(req);

        r.setDayOfWeek(req.dayOfWeek().shortValue());
        r.setStartLocal(parseLocalTime(req.startLocal()));
        r.setWindowMin(req.windowMin());
        // timezone is NEVER updated from the request — it stays = the guide's settings timezone.
        LocalDate effectiveFrom =
                req.effectiveFrom() != null
                        ? parseLocalDate(req.effectiveFrom())
                        : r.getEffectiveFrom();
        LocalDate effectiveTo =
                req.effectiveTo() != null ? parseLocalDate(req.effectiveTo()) : r.getEffectiveTo();
        validateEffectiveRange(effectiveFrom, effectiveTo);
        r.setEffectiveFrom(effectiveFrom);
        r.setEffectiveTo(effectiveTo);
        if (req.active() != null) {
            r.setActive(req.active());
        }

        rules.save(r);
        availabilityService.rematerialize(guideId);
        return toRuleResponse(r);
    }

    @Transactional
    public List<AvailabilityRuleResponse> deleteRule(UserEntity user, UUID id) {
        UUID guideId = requireGuideId(user);
        GuideAvailabilityRuleEntity r =
                rules.findByIdAndGuideId(id, guideId)
                        .orElseThrow(() -> new NotFoundException("Availability rule not found"));
        rules.delete(r);
        availabilityService.rematerialize(guideId);
        return rules.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toRuleResponse)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Exceptions.
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityExceptionResponse> listExceptions(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return exceptions.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toExceptionResponse)
                .toList();
    }

    @Transactional
    public AvailabilityExceptionResponse createException(
            UserEntity user, AvailabilityExceptionRequest req) {
        UUID guideId = requireGuideId(user);
        validateExceptionInput(req);
        // Ensure a settings row exists so rematerialize always has a settings tz to resolve, even
        // for a guide who only ever adds exceptions and never a rule.
        getOrCreateSettings(guideId);

        AvailabilityExceptionEntity e = new AvailabilityExceptionEntity();
        e.setId(UUID.randomUUID());
        e.setGuideId(guideId);
        e.setExceptionDate(parseLocalDate(req.exceptionDate()));
        e.setKind(parseKind(req.kind()));
        e.setStartLocal(parseLocalTime(req.startLocal()));
        e.setWindowMin(req.windowMin());
        e.setReason(req.reason());

        exceptions.save(e);
        availabilityService.rematerialize(guideId);
        return toExceptionResponse(e);
    }

    @Transactional
    public AvailabilityExceptionResponse updateException(
            UserEntity user, UUID id, AvailabilityExceptionRequest req) {
        UUID guideId = requireGuideId(user);
        AvailabilityExceptionEntity e =
                exceptions
                        .findByIdAndGuideId(id, guideId)
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));
        validateExceptionInput(req);

        e.setExceptionDate(parseLocalDate(req.exceptionDate()));
        e.setKind(parseKind(req.kind()));
        e.setStartLocal(parseLocalTime(req.startLocal()));
        e.setWindowMin(req.windowMin());
        e.setReason(req.reason());

        exceptions.save(e);
        availabilityService.rematerialize(guideId);
        return toExceptionResponse(e);
    }

    @Transactional
    public List<AvailabilityExceptionResponse> deleteException(UserEntity user, UUID id) {
        UUID guideId = requireGuideId(user);
        AvailabilityExceptionEntity e =
                exceptions
                        .findByIdAndGuideId(id, guideId)
                        .orElseThrow(
                                () -> new NotFoundException("Availability exception not found"));
        exceptions.delete(e);
        availabilityService.rematerialize(guideId);
        return exceptions.findByGuideId(guideId).stream()
                .map(AvailabilityWriteService::toExceptionResponse)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Settings.
    // ---------------------------------------------------------------------

    @Transactional
    public GuideBookingSettingsResponse getSettings(UserEntity user) {
        UUID guideId = requireGuideId(user);
        return toSettingsResponse(getOrCreateSettings(guideId));
    }

    @Transactional
    public GuideBookingSettingsResponse updateSettings(
            UserEntity user, GuideBookingSettingsUpdateRequest req) {
        UUID guideId = requireGuideId(user);
        GuideBookingSettingsEntity settings = getOrCreateSettings(guideId);
        validateSettingsInput(req);

        String oldTimezone = settings.getTimezone();
        applyUpdates(settings, req);
        // save()/saveAndFlush() may return a DIFFERENT (merged) managed instance than the one
        // passed in -- reassign so the refresh below targets the actually-managed object.
        settings = settingsRepo.saveAndFlush(settings);
        // Refresh (not a repository re-query -- the entity is already identity-mapped in this
        // persistence context, so a plain re-query would just hand back the same in-memory
        // instance without pulling the DB-trigger-owned updated_at) so the response reflects the
        // real DB value.
        entityManager.refresh(settings);

        if (req.timezone() != null && !req.timezone().equals(oldTimezone)) {
            // Cascade invariant: every rule's timezone must equal the settings timezone.
            List<GuideAvailabilityRuleEntity> guideRules = rules.findByGuideId(guideId);
            String newTimezone = settings.getTimezone();
            guideRules.forEach(r -> r.setTimezone(newTimezone));
            rules.saveAll(guideRules);
        }

        availabilityService.rematerialize(guideId);
        return toSettingsResponse(settings);
    }

    // ---------------------------------------------------------------------
    // Guide resolution + IDOR-safe settings provisioning.
    // ---------------------------------------------------------------------

    private UUID requireGuideId(UserEntity user) {
        GuideProfileEntity guide =
                guides.findByUserId(user.getId())
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "No guide profile — complete guide onboarding first"));
        return guide.getId();
    }

    /** Auto-provisions a default settings row for the guide if one does not already exist. */
    private GuideBookingSettingsEntity getOrCreateSettings(UUID guideId) {
        Optional<GuideBookingSettingsEntity> existing = settingsRepo.findByGuideId(guideId);
        if (existing.isPresent()) {
            return existing.get();
        }
        GuideBookingSettingsEntity created = new GuideBookingSettingsEntity();
        created.setGuideId(guideId);
        // save()/saveAndFlush() may return a DIFFERENT (merged) managed instance than the one
        // passed in (the guide_id PK is client-assigned, so Spring Data JPA's isNew() check may
        // route this through merge() rather than persist()) -- reassign so refresh() below targets
        // the actually-managed object, not the detached one we constructed.
        created = settingsRepo.saveAndFlush(created);
        // Refresh so updated_at (DB DEFAULT now(), insertable=false in the entity) is populated --
        // a repository re-query would return the same identity-mapped instance unchanged.
        entityManager.refresh(created);
        return created;
    }

    // ---------------------------------------------------------------------
    // Validation.
    // ---------------------------------------------------------------------

    private static void validateRuleInput(AvailabilityRuleRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.dayOfWeek() == null || req.dayOfWeek() < 0 || req.dayOfWeek() > 6) {
            throw new ValidationException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
        }
        if (req.startLocal() == null || req.startLocal().isBlank()) {
            throw new ValidationException("startLocal is required");
        }
        if (req.windowMin() == null || req.windowMin() <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
    }

    private static void validateEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ValidationException("effectiveTo must not be before effectiveFrom");
        }
    }

    private static void validateExceptionInput(AvailabilityExceptionRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.exceptionDate() == null || req.exceptionDate().isBlank()) {
            throw new ValidationException("exceptionDate is required");
        }
        if (req.kind() == null || req.kind().isBlank()) {
            throw new ValidationException("kind is required");
        }
        if (req.startLocal() == null || req.startLocal().isBlank()) {
            throw new ValidationException("startLocal is required");
        }
        if (req.windowMin() == null || req.windowMin() <= 0) {
            throw new ValidationException("windowMin must be greater than 0");
        }
    }

    private static void validateSettingsInput(GuideBookingSettingsUpdateRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.acceptanceMode() != null) {
            parseAcceptanceMode(req.acceptanceMode());
        }
        if (req.responseDeadlineMin() != null && req.responseDeadlineMin() <= 0) {
            throw new ValidationException("responseDeadlineMin must be greater than 0");
        }
        if (req.minNoticeMin() != null && req.minNoticeMin() < 0) {
            throw new ValidationException("minNoticeMin must not be negative");
        }
        if (req.maxAdvanceDays() != null
                && (req.maxAdvanceDays() <= 0 || req.maxAdvanceDays() > MAX_MAX_ADVANCE_DAYS)) {
            throw new ValidationException(
                    "maxAdvanceDays must be between 1 and " + MAX_MAX_ADVANCE_DAYS);
        }
        if (req.bufferBeforeMin() != null && req.bufferBeforeMin() < 0) {
            throw new ValidationException("bufferBeforeMin must not be negative");
        }
        if (req.bufferAfterMin() != null && req.bufferAfterMin() < 0) {
            throw new ValidationException("bufferAfterMin must not be negative");
        }
        if (req.durationsOffered() != null) {
            if (req.durationsOffered().isEmpty()) {
                throw new ValidationException("durationsOffered must not be empty");
            }
            if (req.durationsOffered().stream().anyMatch(d -> d == null || d <= 0)) {
                throw new ValidationException("durationsOffered entries must be greater than 0");
            }
        }
        if (req.timezone() != null) {
            parseZoneId(req.timezone());
        }
    }

    private static void applyUpdates(
            GuideBookingSettingsEntity settings, GuideBookingSettingsUpdateRequest req) {
        if (req.acceptanceMode() != null) {
            settings.setAcceptanceMode(parseAcceptanceMode(req.acceptanceMode()));
        }
        if (req.responseDeadlineMin() != null) {
            settings.setResponseDeadlineMin(req.responseDeadlineMin());
        }
        if (req.minNoticeMin() != null) {
            settings.setMinNoticeMin(req.minNoticeMin());
        }
        if (req.maxAdvanceDays() != null) {
            settings.setMaxAdvanceDays(req.maxAdvanceDays());
        }
        if (req.bufferBeforeMin() != null) {
            settings.setBufferBeforeMin(req.bufferBeforeMin());
        }
        if (req.bufferAfterMin() != null) {
            settings.setBufferAfterMin(req.bufferAfterMin());
        }
        if (req.durationsOffered() != null) {
            settings.setDurationsOffered(req.durationsOffered());
        }
        if (req.timezone() != null) {
            settings.setTimezone(req.timezone());
        }
    }

    // ---------------------------------------------------------------------
    // Parsing helpers — every one maps a bad value to a domain ValidationException (-> 422), never
    // a framework parse error.
    // ---------------------------------------------------------------------

    private static LocalTime parseLocalTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid startLocal (expected e.g. \"09:00\"): " + raw);
        }
    }

    private static LocalDate parseLocalDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid date (expected e.g. \"2026-07-11\"): " + raw);
        }
    }

    private static AvailabilityExceptionKind parseKind(String raw) {
        try {
            return AvailabilityExceptionKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid kind (expected UNAVAILABLE or ADDITIONAL): " + raw);
        }
    }

    private static AcceptanceMode parseAcceptanceMode(String raw) {
        try {
            return AcceptanceMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid acceptanceMode (expected AUTO or MANUAL): " + raw);
        }
    }

    private static ZoneId parseZoneId(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException ex) {
            throw new ValidationException("Invalid IANA timezone: " + raw);
        }
    }

    // ---------------------------------------------------------------------
    // Response mapping.
    // ---------------------------------------------------------------------

    private static AvailabilityRuleResponse toRuleResponse(GuideAvailabilityRuleEntity r) {
        return new AvailabilityRuleResponse(
                r.getId().toString(),
                r.getDayOfWeek(),
                r.getStartLocal().toString(),
                r.getWindowMin(),
                r.getTimezone(),
                r.getEffectiveFrom() != null ? r.getEffectiveFrom().toString() : null,
                r.getEffectiveTo() != null ? r.getEffectiveTo().toString() : null,
                r.isActive());
    }

    private static AvailabilityExceptionResponse toExceptionResponse(
            AvailabilityExceptionEntity e) {
        return new AvailabilityExceptionResponse(
                e.getId().toString(),
                e.getExceptionDate().toString(),
                e.getKind().name(),
                e.getStartLocal().toString(),
                e.getWindowMin(),
                e.getReason());
    }

    private static GuideBookingSettingsResponse toSettingsResponse(GuideBookingSettingsEntity s) {
        return new GuideBookingSettingsResponse(
                s.getGuideId().toString(),
                s.getAcceptanceMode().name(),
                s.getResponseDeadlineMin(),
                s.getMinNoticeMin(),
                s.getMaxAdvanceDays(),
                s.getBufferBeforeMin(),
                s.getBufferAfterMin(),
                s.getDurationsOffered(),
                s.getTimezone(),
                s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
    }
}
