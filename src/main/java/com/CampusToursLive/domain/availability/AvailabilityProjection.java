package com.CampusToursLive.domain.availability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * PURE, DB-free projection engine: turns a guide's recurring availability rules and one-off
 * exceptions into a coalesced, disjoint, net-available set of UTC {@link AvailabilityInterval}s
 * over a horizon, plus the calendar days a DST transition affected.
 *
 * <p>This class touches no database and no Spring context — it is a plain static function so Task 3
 * (persistence) can call it inside a transaction without any collaborator wiring.
 *
 * <h2>Net-available algorithm</h2>
 *
 * For each day in the horizon: gather every ACTIVE rule whose {@code dayOfWeek} matches that day
 * and whose {@code [effectiveFrom, effectiveTo]} contains it ("base"), every {@code ADDITIONAL}
 * exception on that date ("additional"), and every {@code UNAVAILABLE} exception on that date
 * ("unavailable"). Then, over the whole horizon:
 *
 * <pre>
 *   available     = coalesce(base UNION additional)
 *   unavailableNet = coalesce(unavailable) MINUS coalesce(additional)
 *   net           = available MINUS unavailableNet
 * </pre>
 *
 * Subtracting {@code additional} out of the {@code unavailable} set before the final subtraction is
 * exactly "{@code additional} overrides {@code unavailable} on overlap": any region an {@code
 * ADDITIONAL} exception covers is never removed from {@code available}, even if an {@code
 * UNAVAILABLE} exception also covers it. {@code net} is coalesced into a disjoint, ascending union
 * before being returned.
 *
 * <h2>Exception timezone resolution</h2>
 *
 * {@link AvailabilityExceptionEntity} carries no timezone column — only rules do. Per guide, all
 * active rules are expected to share one IANA zone (the {@code guide_booking_settings.timezone}
 * cascade invariant), so exceptions are resolved in that shared zone. If a guide somehow has rules
 * in mixed zones (a legacy/inconsistent row), this resolves exceptions using the MODE (most
 * frequent) timezone across every rule passed in (not just active ones — inactive/history rows
 * still count toward "the guide's usual zone"), with ties broken by the alphabetically smallest
 * zone id, so the choice is deterministic and reproducible. If exceptions are supplied but no rules
 * are supplied at all, there is no zone to resolve against and {@link #project} throws {@link
 * IllegalArgumentException} rather than guessing (e.g. defaulting to UTC), since silently picking a
 * wrong zone would silently corrupt every exception's projected instant.
 *
 * <h2>DST handling</h2>
 *
 * Each occurrence's start is resolved via {@code ZonedDateTime.of(date, startLocal, ZoneId.of(tz))}
 * (JDK defaults: a spring-forward GAP shifts the local time forward by the gap length; a fall-back
 * AMBIGUOUS local time resolves to the earlier of its two offsets). The end is computed by adding
 * the window's {@link Duration} to the resolved {@link ZonedDateTime} (elapsed-time arithmetic, so
 * it correctly absorbs any DST transition inside the window) and only then converting to {@link
 * Instant}.
 *
 * <p><b>A calendar day is flagged in {@code dstAdjustedDays} when either:</b>
 *
 * <ol>
 *   <li>the resolved start's {@code toLocalTime()} differs from the requested {@code startLocal}
 *       (the requested time fell inside a spring-forward gap and got shifted forward), OR
 *   <li>the resolved start's UTC offset differs from the resolved end's UTC offset (a DST
 *       transition — gap or fall-back overlap — occurred somewhere inside the window, even though
 *       the requested start itself was valid and unshifted).
 * </ol>
 *
 * Case 2 is what catches, e.g., a window that starts cleanly before a fall-back transition and ends
 * after it (the wall-clock reading of the end may equal the start's, since the hour repeats, so
 * comparing local times alone would miss it) or a window that starts before a spring-forward gap
 * and ends after it. A purely ambiguous window that starts and ends without crossing the transition
 * instant is NOT flagged (no shift, no offset change) — the guide picked a real, unambiguous
 * elapsed window; only windows a transition actually touches are reported.
 */
public final class AvailabilityProjection {

    private AvailabilityProjection() {}

    /**
     * Projects {@code rules} + {@code exceptions} (assumed to all belong to one guide — the caller
     * is responsible for pre-filtering by {@code guideId}) over {@code horizon} into a coalesced,
     * disjoint, ascending set of net-available UTC intervals.
     */
    public static ProjectionResult project(
            List<GuideAvailabilityRuleEntity> rules,
            List<AvailabilityExceptionEntity> exceptions,
            AvailabilityHorizon horizon) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(exceptions, "exceptions");
        Objects.requireNonNull(horizon, "horizon");

        // Legacy/3-arg entry point (kept for the pure Task-2 tests): resolve the exception zone
        // from the rules themselves (MODE heuristic). This throws when exceptions are supplied but
        // no rules exist. Callers that know the guide's zone (Task 3, from
        // guide_booking_settings.timezone) should use the 4-arg overload below instead, which never
        // needs a rule to resolve an exception's zone.
        String exceptionTimezone = exceptions.isEmpty() ? null : resolveExceptionTimezone(rules);
        return project(rules, exceptions, horizon, exceptionTimezone);
    }

    /**
     * Same projection, but with the exception zone supplied EXPLICITLY. Rules are always projected
     * in each rule's own {@code timezone} column; every exception is projected in {@code
     * guideTimezone} (the {@code guide_booking_settings.timezone}). This overload lets a guide who
     * has exceptions but NO active rules still materialize correctly — there is no rule to infer a
     * zone from, so the persistence layer passes the guide's settings zone directly instead of the
     * 3-arg overload throwing. {@code guideTimezone} may be {@code null} only when {@code
     * exceptions} is empty (then no exception needs a zone).
     */
    public static ProjectionResult project(
            List<GuideAvailabilityRuleEntity> rules,
            List<AvailabilityExceptionEntity> exceptions,
            AvailabilityHorizon horizon,
            String guideTimezone) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(exceptions, "exceptions");
        Objects.requireNonNull(horizon, "horizon");
        if (!exceptions.isEmpty()) {
            Objects.requireNonNull(
                    guideTimezone,
                    "guideTimezone is required when exceptions are present (exceptions carry no"
                            + " timezone column of their own)");
        }

        String exceptionTimezone = exceptions.isEmpty() ? null : guideTimezone;

        List<AvailabilityInterval> base = new ArrayList<>();
        List<AvailabilityInterval> additional = new ArrayList<>();
        List<AvailabilityInterval> unavailable = new ArrayList<>();
        TreeSet<LocalDate> dstAdjustedDays = new TreeSet<>();

        for (LocalDate date = horizon.from();
                date.isBefore(horizon.toExclusive());
                date = date.plusDays(1)) {
            int isoSundayZeroDow = date.getDayOfWeek().getValue() % 7; // 0=Sun .. 6=Sat

            for (GuideAvailabilityRuleEntity rule : rules) {
                if (!rule.isActive()) {
                    continue;
                }
                if (rule.getDayOfWeek() != isoSundayZeroDow) {
                    continue;
                }
                if (date.isBefore(rule.getEffectiveFrom())) {
                    continue;
                }
                if (rule.getEffectiveTo() != null && date.isAfter(rule.getEffectiveTo())) {
                    continue;
                }

                ResolvedWindow resolved =
                        resolveWindow(
                                date,
                                rule.getStartLocal(),
                                rule.getWindowMin(),
                                rule.getTimezone());
                base.add(resolved.interval());
                if (resolved.dstAdjusted()) {
                    dstAdjustedDays.add(date);
                }
            }

            for (AvailabilityExceptionEntity exception : exceptions) {
                if (!exception.getExceptionDate().equals(date)) {
                    continue;
                }

                ResolvedWindow resolved =
                        resolveWindow(
                                date,
                                exception.getStartLocal(),
                                exception.getWindowMin(),
                                exceptionTimezone);
                if (resolved.dstAdjusted()) {
                    dstAdjustedDays.add(date);
                }

                if (exception.getKind() == AvailabilityExceptionKind.ADDITIONAL) {
                    additional.add(resolved.interval());
                } else {
                    unavailable.add(resolved.interval());
                }
            }
        }

        List<AvailabilityInterval> available = union(base, additional);
        List<AvailabilityInterval> unavailableNet = subtract(unavailable, additional);
        List<AvailabilityInterval> net = subtract(available, unavailableNet);

        return new ProjectionResult(net, new ArrayList<>(dstAdjustedDays));
    }

    /** One rule/exception's local window resolved to a concrete UTC instant range for one date. */
    private record ResolvedWindow(AvailabilityInterval interval, boolean dstAdjusted) {}

    private static ResolvedWindow resolveWindow(
            LocalDate date, LocalTime startLocal, int windowMin, String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime start = ZonedDateTime.of(date, startLocal, zone);
        ZonedDateTime end = start.plus(Duration.ofMinutes(windowMin));

        boolean startShiftedByGap = !start.toLocalTime().equals(startLocal);
        boolean offsetChangedDuringWindow = !start.getOffset().equals(end.getOffset());
        boolean dstAdjusted = startShiftedByGap || offsetChangedDuringWindow;

        return new ResolvedWindow(
                new AvailabilityInterval(start.toInstant(), end.toInstant()), dstAdjusted);
    }

    /**
     * Resolves the shared timezone used to project exceptions (which carry no {@code timezone}
     * column of their own) — see the class-level javadoc "Exception timezone resolution" section.
     *
     * <p>Package-private so the persistence layer ({@link AvailabilityService}) can reuse the exact
     * MODE-of-rules heuristic when a guide has no settings row but does have rules, instead of
     * duplicating it. Still throws on empty {@code rules} — callers that may pass no rules (e.g.
     * exceptions-without-rules) must guard for that and fall back to a default zone themselves.
     */
    static String resolveExceptionTimezone(List<GuideAvailabilityRuleEntity> rules) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot resolve a timezone for exceptions: no rules were supplied. Exceptions"
                            + " carry no timezone column, so at least one rule (any guide rule, active"
                            + " or not) is required to resolve one.");
        }

        Map<String, Long> countsByZone =
                rules.stream()
                        .collect(
                                Collectors.groupingBy(
                                        GuideAvailabilityRuleEntity::getTimezone,
                                        Collectors.counting()));

        long maxCount =
                countsByZone.values().stream().mapToLong(Long::longValue).max().orElseThrow();

        return countsByZone.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    /** Merges overlapping/touching intervals into a disjoint, ascending-by-start union. */
    private static List<AvailabilityInterval> coalesce(List<AvailabilityInterval> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }

        List<AvailabilityInterval> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparing(AvailabilityInterval::startAt));

        List<AvailabilityInterval> merged = new ArrayList<>();
        Instant curStart = sorted.get(0).startAt();
        Instant curEnd = sorted.get(0).endAt();
        for (int i = 1; i < sorted.size(); i++) {
            AvailabilityInterval next = sorted.get(i);
            if (!next.startAt().isAfter(curEnd)) {
                // Overlapping or exactly touching -> merge.
                if (next.endAt().isAfter(curEnd)) {
                    curEnd = next.endAt();
                }
            } else {
                merged.add(new AvailabilityInterval(curStart, curEnd));
                curStart = next.startAt();
                curEnd = next.endAt();
            }
        }
        merged.add(new AvailabilityInterval(curStart, curEnd));
        return merged;
    }

    private static List<AvailabilityInterval> union(
            List<AvailabilityInterval> a, List<AvailabilityInterval> b) {
        List<AvailabilityInterval> combined = new ArrayList<>(a.size() + b.size());
        combined.addAll(a);
        combined.addAll(b);
        return coalesce(combined);
    }

    /**
     * {@code minuend - subtrahend}, both coalesced first; result is coalesced, disjoint, ascending.
     */
    private static List<AvailabilityInterval> subtract(
            List<AvailabilityInterval> minuendRaw, List<AvailabilityInterval> subtrahendRaw) {
        List<AvailabilityInterval> minuend = coalesce(minuendRaw);
        List<AvailabilityInterval> subtrahend = coalesce(subtrahendRaw);
        if (subtrahend.isEmpty()) {
            return minuend;
        }

        List<AvailabilityInterval> result = new ArrayList<>();
        for (AvailabilityInterval m : minuend) {
            Instant cursor = m.startAt();
            for (AvailabilityInterval s : subtrahend) {
                if (!s.endAt().isAfter(cursor)) {
                    continue; // entirely before the remaining part of m
                }
                if (!s.startAt().isBefore(m.endAt())) {
                    break; // sorted ascending -> no further s can intersect m
                }
                if (s.startAt().isAfter(cursor)) {
                    Instant pieceEnd = s.startAt().isBefore(m.endAt()) ? s.startAt() : m.endAt();
                    result.add(new AvailabilityInterval(cursor, pieceEnd));
                }
                if (s.endAt().isAfter(cursor)) {
                    cursor = s.endAt();
                }
                if (!cursor.isBefore(m.endAt())) {
                    break;
                }
            }
            if (cursor.isBefore(m.endAt())) {
                result.add(new AvailabilityInterval(cursor, m.endAt()));
            }
        }
        // Re-coalesce: leftovers from distinct minuend entries could abut after clipping.
        return coalesce(result);
    }
}
