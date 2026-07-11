package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Table-driven tests for the PURE {@link AvailabilityProjection} engine — no DB, no Spring context.
 * These are the correctness spine for CTL-54: every hard case from the plan (DST gap, DST
 * ambiguous, cross-midnight, coalesce, precedence, net/partial-clip, all-day, active/effective
 * exclusion) is asserted against concrete UTC {@link Instant}s, not just interval counts.
 */
class AvailabilityProjectionTest {

    private static final String NY = "America/New_York";
    private static final String CHICAGO = "America/Chicago";
    private static final String DENVER = "America/Denver";

    // ---------------------------------------------------------------------
    // DST gap (spring-forward Sunday, US 2026-03-08: 02:00 -> 03:00 skipped)
    // ---------------------------------------------------------------------

    @Test
    void dstGap_requestedTimeInsideSkippedHour_shiftsForwardAndIsFlagged() {
        LocalDate springForwardSunday = LocalDate.of(2026, 3, 8);
        GuideAvailabilityRuleEntity rule =
                rule(0, LocalTime.of(2, 30), 60, NY, springForwardSunday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(rule),
                        List.of(),
                        new AvailabilityHorizon(
                                springForwardSunday, springForwardSunday.plusDays(1)));

        // 02:30 doesn't exist -> JDK shifts forward by the 1h gap -> resolves to 03:30 -04:00.
        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-08T07:30:00Z"),
                                Instant.parse("2026-03-08T08:30:00Z")));
        assertThat(result.dstAdjustedDays()).containsExactly(springForwardSunday);
    }

    @Test
    void dstGap_windowStartsBeforeGapAndCrossesIt_offsetChangeIsFlaggedEvenWithoutShift() {
        // Start (01:30) is a real, unshifted local time; the window's END crosses the 1h gap.
        LocalDate springForwardSunday = LocalDate.of(2026, 3, 8);
        GuideAvailabilityRuleEntity rule =
                rule(0, LocalTime.of(1, 30), 90, NY, springForwardSunday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(rule),
                        List.of(),
                        new AvailabilityHorizon(
                                springForwardSunday, springForwardSunday.plusDays(1)));

        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-08T06:30:00Z"),
                                Instant.parse("2026-03-08T08:00:00Z")));
        assertThat(result.dstAdjustedDays()).containsExactly(springForwardSunday);
    }

    // ---------------------------------------------------------------------
    // DST ambiguous (fall-back Sunday, US 2026-11-01: 02:00 -> 01:00, 01:xx occurs twice)
    // ---------------------------------------------------------------------

    @Test
    void dstAmbiguous_nonCrossingWindow_choosesEarlierOffsetAndIsNotFlagged() {
        LocalDate fallBackSunday = LocalDate.of(2026, 11, 1);
        // 01:00-01:30 is entirely inside the FIRST (earlier-offset) occurrence of the repeated
        // hour.
        GuideAvailabilityRuleEntity rule =
                rule(0, LocalTime.of(1, 0), 30, NY, fallBackSunday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(rule),
                        List.of(),
                        new AvailabilityHorizon(fallBackSunday, fallBackSunday.plusDays(1)));

        // Earlier offset (-04:00, "EDT/summer" side) chosen, per ZonedDateTime.of defaults.
        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-11-01T05:00:00Z"),
                                Instant.parse("2026-11-01T05:30:00Z")));
        // Neither shifted nor offset-changed within the window -> not flagged.
        assertThat(result.dstAdjustedDays()).isEmpty();
    }

    @Test
    void dstAmbiguous_windowCrossingIntoSecondOccurrence_isFlagged() {
        LocalDate fallBackSunday = LocalDate.of(2026, 11, 1);
        // 01:30 (-04:00) + 60 min elapsed crosses the fall-back instant -> ends at local 01:30
        // again,
        // now at -05:00. Wall-clock start/end look identical but the offset changed mid-window.
        GuideAvailabilityRuleEntity rule =
                rule(0, LocalTime.of(1, 30), 60, NY, fallBackSunday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(rule),
                        List.of(),
                        new AvailabilityHorizon(fallBackSunday, fallBackSunday.plusDays(1)));

        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-11-01T05:30:00Z"),
                                Instant.parse("2026-11-01T06:30:00Z")));
        assertThat(result.dstAdjustedDays()).containsExactly(fallBackSunday);
    }

    // ---------------------------------------------------------------------
    // Cross-midnight: 22:00 + 4h -> ONE interval crossing the day boundary, no wraparound/split.
    // ---------------------------------------------------------------------

    @Test
    void crossMidnight_producesSingleIntervalSpanningTheDayBoundary() {
        LocalDate sunday = LocalDate.of(2026, 3, 1); // plain Sunday, no DST transition nearby
        GuideAvailabilityRuleEntity rule = rule(0, LocalTime.of(22, 0), 240, NY, sunday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(rule),
                        List.of(),
                        new AvailabilityHorizon(sunday, sunday.plusDays(1)));

        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T03:00:00Z"),
                                Instant.parse("2026-03-02T07:00:00Z")));
    }

    // ---------------------------------------------------------------------
    // Coalesce: two overlapping rules -> one merged interval.
    // ---------------------------------------------------------------------

    @Test
    void coalesce_twoOverlappingRules_mergeIntoOneInterval() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity ruleA = rule(1, LocalTime.of(9, 0), 120, NY, monday, null);
        GuideAvailabilityRuleEntity ruleB = rule(1, LocalTime.of(10, 0), 120, NY, monday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(ruleA, ruleB),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T14:00:00Z"),
                                Instant.parse("2026-03-02T17:00:00Z")));
    }

    // ---------------------------------------------------------------------
    // Precedence: ADDITIONAL overlapping UNAVAILABLE -> the additional region stays available.
    // ---------------------------------------------------------------------

    @Test
    void precedence_additionalOverridesUnavailableOnOverlap() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity base =
                rule(1, LocalTime.of(8, 0), 720, NY, monday, null); // 08-20
        AvailabilityExceptionEntity unavailable =
                exception(
                        monday,
                        AvailabilityExceptionKind.UNAVAILABLE,
                        LocalTime.of(10, 0),
                        240); // 10-14
        AvailabilityExceptionEntity additional =
                exception(
                        monday,
                        AvailabilityExceptionKind.ADDITIONAL,
                        LocalTime.of(12, 0),
                        240); // 12-16

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(base),
                        List.of(unavailable, additional),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        // unavailable [10-14) minus additional [12-16) = only [10-12) actually removed;
        // [12-14) survives because ADDITIONAL protects it -> net = [08-10) + [12-20).
        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T13:00:00Z"),
                                Instant.parse("2026-03-02T15:00:00Z")),
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T17:00:00Z"),
                                Instant.parse("2026-03-03T01:00:00Z")));
    }

    // ---------------------------------------------------------------------
    // Net (partial clip): base rule window with an UNAVAILABLE exception clipping the middle.
    // ---------------------------------------------------------------------

    @Test
    void net_unavailableExceptionClipsMiddleOfBaseWindow_leavesTwoIntervals() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity base =
                rule(1, LocalTime.of(9, 0), 480, NY, monday, null); // 09-17
        AvailabilityExceptionEntity unavailable =
                exception(
                        monday,
                        AvailabilityExceptionKind.UNAVAILABLE,
                        LocalTime.of(12, 0),
                        60); // 12-13

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(base),
                        List.of(unavailable),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals())
                .containsExactly(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T14:00:00Z"),
                                Instant.parse("2026-03-02T17:00:00Z")),
                        new AvailabilityInterval(
                                Instant.parse("2026-03-02T18:00:00Z"),
                                Instant.parse("2026-03-02T22:00:00Z")));
    }

    // ---------------------------------------------------------------------
    // All-day case: 1440-minute 00:00 UNAVAILABLE removes the whole day.
    // ---------------------------------------------------------------------

    @Test
    void allDayUnavailableException_removesTheEntireDay() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity base =
                rule(1, LocalTime.of(9, 0), 480, NY, monday, null); // 09-17
        AvailabilityExceptionEntity allDayUnavailable =
                exception(monday, AvailabilityExceptionKind.UNAVAILABLE, LocalTime.of(0, 0), 1440);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(base),
                        List.of(allDayUnavailable),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // active=false and out-of-effective-range rules are excluded.
    // ---------------------------------------------------------------------

    @Test
    void inactiveRule_isExcluded() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity inactive = rule(1, LocalTime.of(9, 0), 60, NY, monday, null);
        inactive.setActive(false);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(inactive),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).isEmpty();
    }

    @Test
    void ruleBeforeEffectiveFrom_isExcluded() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity futureRule =
                rule(1, LocalTime.of(9, 0), 60, NY, monday.plusDays(7), null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(futureRule),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).isEmpty();
    }

    @Test
    void ruleAfterEffectiveTo_isExcluded() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity expiredRule =
                rule(1, LocalTime.of(9, 0), 60, NY, monday.minusDays(30), monday.minusDays(1));

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(expiredRule),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).isEmpty();
    }

    @Test
    void ruleWithinEffectiveRange_isIncluded() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity inRange =
                rule(1, LocalTime.of(9, 0), 60, NY, monday.minusDays(30), monday.plusDays(30));

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(inRange),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).hasSize(1);
    }

    // ---------------------------------------------------------------------
    // Exception timezone resolution.
    // ---------------------------------------------------------------------

    @Test
    void exceptionTimezone_resolvesToModeAcrossAllRulesRegardlessOfDay() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate tuesday = LocalDate.of(2026, 3, 3);
        LocalDate wednesday = LocalDate.of(2026, 3, 4); // matches neither rule's day_of_week

        GuideAvailabilityRuleEntity nyRuleA = rule(1, LocalTime.of(9, 0), 60, NY, monday, null);
        GuideAvailabilityRuleEntity nyRuleB = rule(2, LocalTime.of(9, 0), 60, NY, monday, null);
        GuideAvailabilityRuleEntity laRule =
                rule(2, LocalTime.of(9, 0), 60, "America/Los_Angeles", monday, null);

        AvailabilityExceptionEntity additional =
                exception(wednesday, AvailabilityExceptionKind.ADDITIONAL, LocalTime.of(10, 0), 60);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(nyRuleA, nyRuleB, laRule),
                        List.of(additional),
                        new AvailabilityHorizon(monday, wednesday.plusDays(1)));

        // NY appears twice vs LA once -> mode = NY (-05:00 in early March) for the exception.
        assertThat(result.intervals())
                .contains(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-04T15:00:00Z"),
                                Instant.parse("2026-03-04T16:00:00Z")));
    }

    @Test
    void exceptionTimezone_tieBrokenAlphabetically() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate tuesday = LocalDate.of(2026, 3, 3);
        LocalDate wednesday = LocalDate.of(2026, 3, 4);

        GuideAvailabilityRuleEntity denverRule =
                rule(1, LocalTime.of(9, 0), 60, DENVER, monday, null);
        GuideAvailabilityRuleEntity chicagoRule =
                rule(2, LocalTime.of(9, 0), 60, CHICAGO, monday, null);

        AvailabilityExceptionEntity additional =
                exception(wednesday, AvailabilityExceptionKind.ADDITIONAL, LocalTime.of(10, 0), 60);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(denverRule, chicagoRule),
                        List.of(additional),
                        new AvailabilityHorizon(monday, wednesday.plusDays(1)));

        // Tie (1 rule each) -> alphabetically smallest zone id wins: "America/Chicago" (-06:00).
        assertThat(result.intervals())
                .contains(
                        new AvailabilityInterval(
                                Instant.parse("2026-03-04T16:00:00Z"),
                                Instant.parse("2026-03-04T17:00:00Z")));
    }

    @Test
    void exceptionTimezone_noRulesSupplied_throws() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        AvailabilityExceptionEntity additional =
                exception(monday, AvailabilityExceptionKind.ADDITIONAL, LocalTime.of(10, 0), 60);

        assertThatThrownBy(
                        () ->
                                AvailabilityProjection.project(
                                        List.of(),
                                        List.of(additional),
                                        new AvailabilityHorizon(monday, monday.plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------
    // General invariants.
    // ---------------------------------------------------------------------

    @Test
    void noRulesNoExceptions_producesEmptyResult() {
        LocalDate monday = LocalDate.of(2026, 3, 2);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(), List.of(), new AvailabilityHorizon(monday, monday.plusDays(1)));

        assertThat(result.intervals()).isEmpty();
        assertThat(result.dstAdjustedDays()).isEmpty();
    }

    @Test
    void intervals_areAlwaysAscendingAndDisjoint() {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        GuideAvailabilityRuleEntity morning = rule(1, LocalTime.of(6, 0), 120, NY, monday, null);
        GuideAvailabilityRuleEntity afternoon = rule(1, LocalTime.of(14, 0), 120, NY, monday, null);
        GuideAvailabilityRuleEntity evening = rule(1, LocalTime.of(20, 0), 120, NY, monday, null);

        ProjectionResult result =
                AvailabilityProjection.project(
                        List.of(evening, morning, afternoon),
                        List.of(),
                        new AvailabilityHorizon(monday, monday.plusDays(1)));

        List<AvailabilityInterval> intervals = result.intervals();
        assertThat(intervals).hasSize(3);
        for (int i = 1; i < intervals.size(); i++) {
            assertThat(intervals.get(i).startAt()).isAfter(intervals.get(i - 1).endAt());
        }
    }

    // ---------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------

    private static GuideAvailabilityRuleEntity rule(
            int dayOfWeek,
            LocalTime startLocal,
            int windowMin,
            String timezone,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(UUID.randomUUID());
        rule.setDayOfWeek((short) dayOfWeek);
        rule.setStartLocal(startLocal);
        rule.setWindowMin(windowMin);
        rule.setTimezone(timezone);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setActive(true);
        return rule;
    }

    private static AvailabilityExceptionEntity exception(
            LocalDate exceptionDate,
            AvailabilityExceptionKind kind,
            LocalTime startLocal,
            int windowMin) {
        AvailabilityExceptionEntity exception = new AvailabilityExceptionEntity();
        exception.setId(UUID.randomUUID());
        exception.setGuideId(UUID.randomUUID());
        exception.setExceptionDate(exceptionDate);
        exception.setKind(kind);
        exception.setStartLocal(startLocal);
        exception.setWindowMin(windowMin);
        return exception;
    }
}
