package com.CampusToursLive.domain.guide;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for enrolment-year rules (spec I1). Both the validator ({@link
 * GuideService}) and the endpoint that publishes the rules to the browser read THIS — neither keeps
 * its own copy, so "the API says yes, validation says no" cannot happen.
 *
 * <p>Every year comes from the injected UTC {@link Clock} (I2). Do not introduce an ambient
 * now-reading call here or at any call site — see the prohibition in the plan's Global Constraints.
 * (Deliberately paraphrased rather than spelled out: Task 8's gate greps for those call
 * expressions, and a Javadoc quoting one verbatim would trip it.)
 */
@Component
public class EnrollmentYearRules {

    /** An inclusive [min, max] year window. */
    public record YearRange(int min, int max) {}

    /**
     * One row of the degree table: if the degree contains ANY of {@code matches}, the programme
     * takes at most {@code years} to finish. Order across rows is significant (first hit wins);
     * order within a row is not.
     */
    public record DegreeRule(List<String> matches, int years) {}

    /** Years back from the current year that an enrolment may have happened. */
    private static final int ENTRY_YEAR_FLOOR = 10;

    /** Years forward — admits someone holding an offer for the coming academic year. */
    private static final int ENTRY_YEAR_CEILING = 1;

    /** Cache lifetime ceiling; the real age also contracts to the year boundary. */
    private static final long MAX_CACHE_SECONDS = 86_400L;

    private static final int DEFAULT_MAX_YEARS_TO_GRADUATE = 8;

    /**
     * Declared order IS evaluation order and IS the order published to clients, which apply the
     * same first-hit rule. Reordering these rows changes behaviour.
     */
    private static final List<DegreeRule> DEGREE_RULES =
            List.of(
                    new DegreeRule(List.of("doctor", "first professional"), 9),
                    new DegreeRule(List.of("master", "post-baccalaureate"), 3),
                    new DegreeRule(List.of("bachelor"), 6),
                    new DegreeRule(List.of("associate", "certificate", "diploma"), 3));

    private final Clock clock;

    public EnrollmentYearRules(Clock clock) {
        this.clock = clock;
    }

    /**
     * ONE response's worth of clock-derived values, read at a single instant. Anything serving a
     * response must build it from this, never from separate calls.
     */
    public record EnrollmentYearSnapshot(YearRange entryYear, long cacheMaxAgeSeconds) {}

    /**
     * Reads the clock EXACTLY ONCE and derives both clock-dependent values from that instant.
     *
     * <p>A shared Clock bean guarantees a single source, not a single reading. Two reads that
     * straddle midnight UTC describe different years: a countdown computed against the later year
     * runs to the wrong boundary, and a window from the earlier year beside an age from the later
     * one is precisely the disagreement I2 promises cannot happen. The fix is not a "better" clock
     * — it is taking one instant and using it for everything.
     */
    public EnrollmentYearSnapshot snapshot() {
        Instant now = clock.instant();
        int year = now.atZone(ZoneOffset.UTC).getYear();
        Instant nextYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long maxAge = Math.min(Duration.between(now, nextYear).getSeconds(), MAX_CACHE_SECONDS);
        return new EnrollmentYearSnapshot(
                new YearRange(year - ENTRY_YEAR_FLOOR, year + ENTRY_YEAR_CEILING), maxAge);
    }

    /**
     * The entry-year window alone, for validation, which needs no cache age. Delegates to {@link
     * #snapshot()} so there is one derivation, not two that could drift apart.
     */
    public YearRange entryYearRange() {
        return snapshot().entryYear();
    }

    /**
     * Longest time to graduate, counted FROM ENROLMENT (not from today — that is the change this
     * work makes). Case-insensitive with {@link Locale#ROOT}: a default-locale lowercase maps
     * {@code I} to a dotless {@code ı} under a Turkish locale and would silently stop matching.
     *
     * <p>A null or blank degree returns the default, but that is NULL-TOLERANCE, not a business
     * rule: {@code degree} is required and {@code GuideService} rejects a missing one before
     * validation runs, so no real request reaches here without it. The tolerance exists so this
     * stays a total function — do not read it as "a guide with no degree silently gets 8 years".
     */
    public int maxYearsToGraduate(String degree) {
        String needle = degree == null ? "" : degree.trim().toLowerCase(Locale.ROOT);
        for (DegreeRule rule : DEGREE_RULES) {
            for (String match : rule.matches()) {
                if (needle.contains(match)) {
                    return rule.years();
                }
            }
        }
        return DEFAULT_MAX_YEARS_TO_GRADUATE;
    }

    /** Graduating in your enrolment year is not a real case; one-year programmes are. */
    public YearRange classYearRange(int entryYear, String degree) {
        return new YearRange(entryYear + 1, entryYear + maxYearsToGraduate(degree));
    }

    public List<DegreeRule> degreeRules() {
        return DEGREE_RULES;
    }

    public int defaultMaxYearsToGraduate() {
        return DEFAULT_MAX_YEARS_TO_GRADUATE;
    }
}
