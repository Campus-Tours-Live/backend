package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
import com.CampusToursLive.web.dto.ResolvedOccurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide-facing resolved-availability READ (CTL-54 Task 5b) -- the single {@code GET /availability}
 * contract CTL-55/CTL-56 consume for the "actual availability" preview: the guide's editable rules
 * (reused from {@link AvailabilityWriteService}'s rule mapping), the backend-coalesced occurrences
 * (already disjoint + ascending -- see {@link
 * GuideAvailabilityOccurrenceRepository#findByGuideIdOrderByDuringStartAtAsc(UUID)}), and the DST
 * gap-moved/skipped days the projection reported (see {@link
 * GuideAvailabilityDstNoticeRepository}).
 *
 * <p>This is a PURE READ: unlike {@link AvailabilityWriteService}, it never calls {@link
 * AvailabilityService#rematerialize(UUID)} -- the write path (Task 5) and the horizon roll-forward
 * job (Task 4) are the only owners of materialization. The frontend/BFF render the occurrences
 * returned here as-is; they must NOT re-coalesce (single source of truth).
 *
 * <p><b>guideId resolution</b> is REPLICATED here (not extracted into a shared helper) from {@link
 * AvailabilityWriteService}'s private {@code requireGuideId}: it is a 3-line {@code
 * guide_profiles.id} lookup by {@code user_id}, and duplicating it keeps this read feature's diff
 * confined to new files -- it does not touch the write service's tested surface at all. If a third
 * consumer needs the same resolution, extracting a shared helper then would be the better trade.
 */
@Service
public class AvailabilityReadService {

    private final GuideAvailabilityRuleRepository rules;
    private final GuideAvailabilityOccurrenceRepository occurrences;
    private final GuideAvailabilityDstNoticeRepository dstNotices;
    private final GuideProfileRepository guides;

    @Autowired
    public AvailabilityReadService(
            GuideAvailabilityRuleRepository rules,
            GuideAvailabilityOccurrenceRepository occurrences,
            GuideAvailabilityDstNoticeRepository dstNotices,
            GuideProfileRepository guides) {
        this.rules = rules;
        this.occurrences = occurrences;
        this.dstNotices = dstNotices;
        this.guides = guides;
    }

    /**
     * Assembles the resolved-availability read for the caller's own guide profile. {@code from} /
     * {@code to} are optional ISO {@code yyyy-MM-dd} bounds; when both are present only occurrences
     * intersecting the half-open window {@code [from, to)} are returned, when either is absent that
     * side of the window is unbounded, and when both are absent every materialized occurrence is
     * returned.
     */
    @Transactional(readOnly = true)
    public ResolvedAvailabilityResponse getResolvedAvailability(
            UserEntity user, String from, String to) {
        UUID guideId = requireGuideId(user);

        Instant windowStart = parseWindowBound(from, "from");
        Instant windowEnd = parseWindowBound(to, "to");
        if (windowStart != null && windowEnd != null && !windowEnd.isAfter(windowStart)) {
            throw new ValidationException("to must be after from");
        }

        List<AvailabilityRuleResponse> ruleResponses =
                rules.findByGuideId(guideId).stream()
                        .map(AvailabilityReadService::toRuleResponse)
                        .toList();

        // findByGuideIdOrderByDuringStartAtAsc already returns a coalesced, disjoint, ascending
        // set (Task 2/3) -- filter in Java (trivial per-guide row count) and never re-coalesce.
        List<ResolvedOccurrence> occurrenceResponses =
                occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId).stream()
                        .filter(o -> intersectsWindow(o, windowStart, windowEnd))
                        .map(o -> new ResolvedOccurrence(o.getDuringStartAt(), o.getDuringEndAt()))
                        .toList();

        List<String> dstGapDays =
                dstNotices.findByGuideId(guideId).stream()
                        .map(GuideAvailabilityDstNoticeEntity::getAdjustedDate)
                        .distinct()
                        .sorted()
                        .map(LocalDate::toString)
                        .toList();

        // Derived readiness signals (CTL-54 v2.1 B1, Contract B) -- computed, never stored:
        //   bookable       = the guide has an occurrence that has not yet ended (something to
        // book).
        //   hasWeeklyHours = the guide has at least one active weekly rule (an expired-but-active
        //                    rule still counts; a soft-deleted/inactive rule does not).
        boolean bookable = occurrences.existsByGuideIdAndDuringEndAtAfter(guideId, Instant.now());
        boolean hasWeeklyHours = rules.existsByGuideIdAndActiveTrue(guideId);

        return new ResolvedAvailabilityResponse(
                ruleResponses, occurrenceResponses, dstGapDays, bookable, hasWeeklyHours);
    }

    /** {@code [windowStart, windowEnd)} intersection; a null bound is unbounded on that side. */
    private static boolean intersectsWindow(
            GuideAvailabilityOccurrenceEntity occurrence, Instant windowStart, Instant windowEnd) {
        boolean startsBeforeWindowEnd =
                windowEnd == null || occurrence.getDuringStartAt().isBefore(windowEnd);
        boolean endsAfterWindowStart =
                windowStart == null || occurrence.getDuringEndAt().isAfter(windowStart);
        return startsBeforeWindowEnd && endsAfterWindowStart;
    }

    private UUID requireGuideId(UserEntity user) {
        GuideProfileEntity guide =
                guides.findByUserId(user.getId())
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "No guide profile -- complete guide onboarding"
                                                        + " first"));
        return guide.getId();
    }

    private static Instant parseWindowBound(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "Invalid " + paramName + " (expected e.g. \"2026-07-11\"): " + raw);
        }
    }

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
}
